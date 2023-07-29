package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	static {
		typeMap.put(StTipoIdentificativoUnivoco.G, SenderTypeEnum.LEGAL_PERSON);
		typeMap.put(StTipoIdentificativoUnivoco.A, SenderTypeEnum.ABI_CODE);
		typeMap.put(StTipoIdentificativoUnivoco.B, SenderTypeEnum.BIC_CODE);
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
					path = "pagopadweufdrsaxmlsharefile/{fileName}",
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
			String psp = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();

			CreateFlowRequest createFlowRequest = new CreateFlowRequest();
			createFlowRequest.setFdr(nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso());
			createFlowRequest.setReportingFlowDate(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
			createFlowRequest.setSender(getSender(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
			createFlowRequest.setReceiver(getReceiver(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
			createFlowRequest.setRegulation(ctFlussoRiversamento.getIdentificativoUnivocoRegolamento());
			createFlowRequest.setRegulationDate(ctFlussoRiversamento.getDataRegolamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
			createFlowRequest.setBicCodePouringBank(ctFlussoRiversamento.getCodiceBicBancaDiRiversamento());
			ApiResponse<GenericResponse> createResponse = getPspApi().internalFdrCreateWithHttpInfo(fdr, psp, createFlowRequest);


        	logger.info("Done processing events");
		} catch (ApiException e) {
			logger.log(Level.SEVERE, "Http error at "+ LocalDateTime.now(), e);
		} catch (Exception e) {
            logger.log(Level.SEVERE, "Generic exception at "+ LocalDateTime.now(), e);
        }
    }


	private Sender getSender(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
		StTipoIdentificativoUnivoco tipoIdentificativoUnivoco = ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getTipoIdentificativoUnivoco();

		Sender sender = new Sender();
		sender.setType(typeMap.get(tipoIdentificativoUnivoco));
		sender.setId(ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getCodiceIdentificativoUnivoco());
		sender.setPsp(nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP());
		sender.setPspName(ctFlussoRiversamento.getIstitutoMittente().getDenominazioneMittente());
		sender.setBrokerId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoIntermediarioPSP());
		sender.setChannelId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoCanale());
		sender.setPassword(nodoInviaFlussoRendicontazioneRequest.getPassword());
		return sender;
	}
	private Receiver getReceiver(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
		Receiver receiver = new Receiver();
		receiver.setId(ctFlussoRiversamento.getIstitutoRicevente().getIdentificativoUnivocoRicevente().getCodiceIdentificativoUnivoco());
		receiver.setEc(nodoInviaFlussoRendicontazioneRequest.getIdentificativoDominio());
		receiver.setEcName(ctFlussoRiversamento.getIstitutoRicevente().getDenominazioneRicevente());
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
