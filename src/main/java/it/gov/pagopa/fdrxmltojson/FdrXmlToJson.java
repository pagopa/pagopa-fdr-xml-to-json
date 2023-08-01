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
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {
	private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";
	private static final String ENV_FDR_NEW_BASE_URL = "FDR_NEW_BASE_URL";
	private static final String ENV_FDR_NEW_API_KEY = "FDR_NEW_API_KEY";
	private static final String ENV_ADD_PAYMENT_REQUEST_PARTITION_SIZE = "ADD_PAYMENT_REQUEST_PARTITION_SIZE";

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
			String url = System.getenv(ENV_FDR_NEW_BASE_URL);
			String apiKey = System.getenv(ENV_FDR_NEW_API_KEY);

			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(apiKey);
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
        try {
			logger.info("Java Blob trigger function processed a blob.\n Name: " + fileName + "\n Size: " + content.length + " Bytes");

			// read xml
			Document document = loadXMLString(content);
			Element element = searchNodeByName(document, NODO_INVIA_FLUSSO_RENDICONTAZIONE);
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = getInstanceByNode(element, NodoInviaFlussoRendicontazioneRequest.class);
			CtFlussoRiversamento ctFlussoRiversamento = getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);
			logger.info("Id flusso: "+ctFlussoRiversamento.getIdentificativoFlusso());

			// extract pathparam for FDR
			String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
			String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();

			// create body for create FDR
			CreateRequest createRequest = getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);
			// call create FDR
			logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_CREATE.name());
			manageHttpError(logger, HttpEventTypeEnum.INTERNAL_CREATE, fileName, () ->
				getPspApi().internalCreate(fdr, pspId, createRequest)
			);

			// create body for addPayment FDR (partitioned)
			List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
			logger.info("Tot CtDatiSingoliPagamenti ["+datiSingoliPagamenti.size()+"]");
			int partitionSize = Integer.valueOf(System.getenv(ENV_ADD_PAYMENT_REQUEST_PARTITION_SIZE));
			List<AddPaymentRequest> addPaymentRequestList = getAddPaymentRequestListPartioned(datiSingoliPagamenti, partitionSize);
			logger.info("Tot AddPaymentRequest=["+addPaymentRequestList.size()+"], partioned by ["+partitionSize+"]");
			// call addPayment FDR for every patition
			for(AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
				logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_ADD_PAYMENT.name()+" (partition=" + addPaymentRequestList.indexOf(addPaymentRequest) + 1 + ", tot payments=" + addPaymentRequest.getPayments().size() + ")");
				manageHttpError(logger, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, fileName, () ->
					getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest)
				);
			}

			// call publish FDR
			logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_PUBLISH.name());
			manageHttpError(logger, HttpEventTypeEnum.INTERNAL_PUBLISH, fileName, () ->
				getPspApi().internalPublish(fdr, pspId)
			);


			//TODO cancellare da blob il file? (la funzione cancellerà)
			logger.info("Done processing events");
		} catch (AppException e) {
			logger.info("Failure processing events");
		} catch (Exception e) {
			Instant now = Instant.now();
			String errorCode = "GENERIC_ERROR";
			String message = getErrorMessage(errorCode, fileName, now);
			logger.log(Level.SEVERE, message, e);
			sendGenericError(now, fileName, errorCode, e);
			logger.info("Failure processing events");
        }
    }

	private enum HttpEventTypeEnum {
		INTERNAL_CREATE,
		INTERNAL_ADD_PAYMENT,
		INTERNAL_PUBLISH;
	}

	@FunctionalInterface
	public interface SupplierWithApiException<T> {
		T get() throws ApiException;
	}

	private static String getHttpErrorMessage(String errorCode, String fileName, HttpEventTypeEnum httpEventTypeEnum, Instant now){
		return "[ALERT] [errorCode="+errorCode+"] [fileName"+fileName+"] [httpEventTypeEnum"+httpEventTypeEnum.name()+"] Http error at "+ now;
	}
	private static String getErrorMessage(String errorCode, String fileName, Instant now){
		return "[ALERT] [errorCode="+errorCode+"] [fileName"+fileName+"] Http error at "+ now;
	}
	private static<T> void manageHttpError(Logger logger, HttpEventTypeEnum httpEventTypeEnum, String fileName, SupplierWithApiException<T> fn){
		try {
			fn.get();
		} catch (ApiException e) {
			String errorResposne = e.getResponseBody();
			String errorCode = null;
			try {
				errorCode = ErrorResponse.fromJson(errorResposne).getAppErrorCode();
			} catch (IOException ex) {
				errorCode = "ERROR_PARSE_RESPONSE";
			}
			Instant now = Instant.now();
			String message = getHttpErrorMessage(errorCode, fileName, httpEventTypeEnum, now);
			logger.log(Level.SEVERE, message, e);
			sendHttpError(now, fileName, errorCode, httpEventTypeEnum, errorResposne, e);
			throw new AppException(message, e);
		}
	}

	private static void sendGenericError(Instant now, String fileName, String errorCode, Exception e){
		_sendToErrorTable(now, fileName, errorCode,Optional.empty(), Optional.empty(), e);
	}
	private static void sendHttpError(Instant now, String fileName, String errorCode, HttpEventTypeEnum httpEventTypeEnum, String errorResposne, Exception e){
		_sendToErrorTable(now, fileName, errorCode,Optional.of(httpEventTypeEnum), Optional.of(errorResposne), e);
	}
	private static void _sendToErrorTable(Instant now, String fileName, String errorCode, Optional<HttpEventTypeEnum> httpEventTypeEnum, Optional<String> errorResposne, Exception e){
		//TODO
	}

	private List<AddPaymentRequest> getAddPaymentRequestListPartioned(List<CtDatiSingoliPagamenti> datiSingoliPagamentiList, int size){
		List<List<CtDatiSingoliPagamenti>> datiSingoliPagamentiPartitioned = Lists.partition(datiSingoliPagamentiList, size);
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

