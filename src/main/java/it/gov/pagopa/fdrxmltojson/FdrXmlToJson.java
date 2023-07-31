package it.gov.pagopa.fdrxmltojson;

import com.google.common.collect.Lists;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivoco;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.ApiResponse;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */

	private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";

	private static InternalPspApi pspApi = null;

	private static final Map<StTipoIdentificativoUnivoco, SenderTypeEnum> typeMap = new LinkedHashMap<>();
	private static final Map<String, PaymentStatusEnum> payStatusMap = new LinkedHashMap<>();
	static {
		typeMap.put(StTipoIdentificativoUnivoco.G, SenderTypeEnum.LEGAL_PERSON);
		typeMap.put(StTipoIdentificativoUnivoco.A, SenderTypeEnum.ABI_CODE);
		typeMap.put(StTipoIdentificativoUnivoco.B, SenderTypeEnum.BIC_CODE);

		payStatusMap.put("0", PaymentStatusEnum.EXECUTED);
		payStatusMap.put("3", PaymentStatusEnum.REVOKED);
		payStatusMap.put("9", PaymentStatusEnum.NO_RPT);
	}

	private static InternalPspApi getPspApi(){
		if(pspApi==null){
			String url = System.getenv("FDR_NEW_BASE_URL");
			String apiKey = System.getenv("FDR_NEW_API_KEY");

			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(apiKey);
//			apiClient.setBasePath(url);
			pspApi = new InternalPspApi(apiClient);
			pspApi.setCustomBaseUrl(url);
		}
		return pspApi;
	}

    @FunctionName("BlobFdrXmlToJsonEventProcessor")
    public void processNodoReEvent (
			@BlobTrigger(
					name = "xmlTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "xmlsharefile/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		logger.info("Java Blob trigger function processed a blob.\n Name: " + fileName + "\n Size: " + content.length + " Bytes");

        try {
			Document document = loadXMLString(content);
			Element element = searchNodeByName(document, NODO_INVIA_FLUSSO_RENDICONTAZIONE);
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = getInstanceByNode(element, NodoInviaFlussoRendicontazioneRequest.class);
			CtFlussoRiversamento ctFlussoRiversamento = getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);
			logger.info("Id flusso: "+ctFlussoRiversamento.getIdentificativoFlusso());

			String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
			String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();

			CreateRequest createRequest = getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);
			try {
				logger.info("Calling... internalCreate");
				getPspApi().internalCreate(fdr, pspId, createRequest);
			} catch (ApiException e) {
				ErrorResponse errorResponse = ErrorResponse.fromJson(e.getResponseBody());
				if("FDR-0702".equals(errorResponse.getAppErrorCode())){
					//se esiste già prova a cancellare e ricreare
					getPspApi().internalDelete(fdr, pspId);
					getPspApi().internalCreate(fdr, pspId, createRequest);
				} else {
					//per qualsiasi altro errore va indagato lo specifico caso
					String message = "Http error internalCreate at "+ LocalDateTime.now();
					logger.log(Level.SEVERE, message, e);
					//TODO spostare in una cosa di errori
					throw new AppException(message, e);
				}
			}

			List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
			logger.info("Tot CtDatiSingoliPagamenti ["+datiSingoliPagamenti.size()+"]");
			int partitionSize = Integer.valueOf(System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE"));
			List<AddPaymentRequest> addPaymentRequestList = getAddPaymentRequestListPartioned(datiSingoliPagamenti, partitionSize);
			logger.info("Tot AddPaymentRequest=["+addPaymentRequestList.size()+"], partioned by ["+partitionSize+"]");
			try {
				for(AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
					logger.info("Calling... (partition=" + addPaymentRequestList.indexOf(addPaymentRequest) + 1 + ") internalAddPayment (tot payments [" + addPaymentRequest.getPayments().size() + "])");
					getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
				}
			} catch (ApiException e) {
				// per qualsiasi errore
				String message = "Http error internalAddPayment at "+ LocalDateTime.now();
				logger.log(Level.SEVERE, message, e);
				//TODO spostare in una cosa di errori
				throw new AppException(message, e);
			}

			try {
				logger.info("Calling... internalPublish");
				getPspApi().internalPublish(fdr, pspId);
			} catch (ApiException e) {
				// per qualsiasi errore
				String message = "Http error internalPublish at "+ LocalDateTime.now();
				logger.log(Level.SEVERE, message, e);
				//TODO spostare in una cosa di errori
				throw new AppException(message, e);
			}

			//TODO cancellare da blob il file?
			logger.info("Done processing events");
		} catch (AppException e) {
		} catch (Exception e) {
            logger.log(Level.SEVERE, "Generic exception at "+ LocalDateTime.now(), e);
        }
    }

	private List<AddPaymentRequest> getAddPaymentRequestListPartioned(List<CtDatiSingoliPagamenti> datiSingoliPagamentiList, int size){
		List<List<CtDatiSingoliPagamenti>> datiSingoliPagamentiPartitioned = Lists.partition(datiSingoliPagamentiList, size);

//		List<AddPaymentRequest> addPaymentRequestList = new ArrayList<>();
//		for(int i = 0; i<datiSingoliPagamentiPartitioned.size();i++){
//			AddPaymentRequest addPaymentRequest = new AddPaymentRequest();
//			for(int a = 0; a<datiSingoliPagamentiPartitioned.get(i).size();a++){
//				addPaymentRequest.addPaymentsItem(getPayment(datiSingoliPagamentiPartitioned.get(i).get(a)));
//			}
//			addPaymentRequestList.add(addPaymentRequest);
//		}
//		return addPaymentRequestList;
		return datiSingoliPagamentiPartitioned.stream()
				.map(datiSingoliPagamentiListPartion -> {
					AddPaymentRequest addPaymentRequest = new AddPaymentRequest();
					addPaymentRequest.setPayments(datiSingoliPagamentiListPartion
							.stream()
							.map(this::getPayment)
							.collect(Collectors.toList()));
					return addPaymentRequest;
				})
				.collect(Collectors.toList());
	}
	private Payment getPayment(CtDatiSingoliPagamenti ctDatiSingoliPagamenti){
		Payment payment = new Payment();
		payment.setIndex(ctDatiSingoliPagamenti.getIndiceDatiSingoloPagamento().longValue());
		payment.setIuv(ctDatiSingoliPagamenti.getIdentificativoUnivocoVersamento());
		payment.setIur(ctDatiSingoliPagamenti.getIdentificativoUnivocoRiscossione());
		payment.setPay(ctDatiSingoliPagamenti.getSingoloImportoPagato().doubleValue());
		payment.setPayDate(ctDatiSingoliPagamenti.getDataEsitoSingoloPagamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
		payment.setPayStatus(payStatusMap.get(ctDatiSingoliPagamenti.getCodiceEsitoSingoloPagamento()));
		return payment;
	}
	private CreateRequest getCreateRequest(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
		CreateRequest createRequest = new CreateRequest();
		createRequest.setFdr(nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso());
		createRequest.setFdrDate(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
		createRequest.setSender(getSender(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
		createRequest.setReceiver(getReceiver(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
		createRequest.setRegulation(ctFlussoRiversamento.getIdentificativoUnivocoRegolamento());
		createRequest.setRegulationDate(ctFlussoRiversamento.getDataRegolamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
		createRequest.setBicCodePouringBank(ctFlussoRiversamento.getCodiceBicBancaDiRiversamento());
		createRequest.setTotPayments(ctFlussoRiversamento.getNumeroTotalePagamenti().longValue());
		createRequest.setSumPayments(ctFlussoRiversamento.getImportoTotalePagamenti().doubleValue());
		return createRequest;
	}

	private Sender getSender(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
		StTipoIdentificativoUnivoco tipoIdentificativoUnivoco = ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getTipoIdentificativoUnivoco();

		Sender sender = new Sender();
		sender.setType(typeMap.get(tipoIdentificativoUnivoco));
		sender.setId(ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getCodiceIdentificativoUnivoco());
		sender.setPspId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP());
		sender.setPspName(ctFlussoRiversamento.getIstitutoMittente().getDenominazioneMittente());
		sender.setPspBrokerId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoIntermediarioPSP());
		sender.setChannelId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoCanale());
		sender.setPassword(nodoInviaFlussoRendicontazioneRequest.getPassword());
		return sender;
	}
	private Receiver getReceiver(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
		String organizationId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoDominio();
		String organizationName = ctFlussoRiversamento.getIstitutoRicevente().getDenominazioneRicevente();

		Receiver receiver = new Receiver();
		receiver.setId(ctFlussoRiversamento.getIstitutoRicevente().getIdentificativoUnivocoRicevente().getCodiceIdentificativoUnivoco());
		receiver.setOrganizationId(organizationId);
		receiver.setOrganizationName(organizationName!=null?organizationName:organizationId);
		return receiver;
	}

	private <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(type);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
		return jaxb.getValue();
	}
	private <T> T getInstanceByNode(Node node, Class<T> type) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(type);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<T> jaxb =  unmarshaller.unmarshal(node, type);
		return jaxb.getValue();
	}
	public static Document loadXMLString(byte[] content) throws Exception{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		InputSource is = new InputSource(new ByteArrayInputStream(content));
		return db.parse(is);
	}

	private Element searchNodeByName(Node node, String elementToSearch) {
		NodeList nodeList = node.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node currentode = nodeList.item(i);
			if (currentode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) currentode;
				if(element.getTagName().endsWith(elementToSearch)){
					return element;
				} else {
					return searchNodeByName(element, elementToSearch);
				}
			}
		}
		return null;
	}
}

