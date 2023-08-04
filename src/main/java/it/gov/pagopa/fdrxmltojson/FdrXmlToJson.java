package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
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
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {
	private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";


	private static final String fdrNewBaseUrl = System.getenv("FDR_NEW_BASE_URL");
	private static final String fdrNewApiKey = System.getenv("FDR_NEW_API_KEY");
	private static final String addPaymentRequestPartitionSize = System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE");

	private static final String tableStorageConnString = System.getenv("TABLE_STORAGE_CONN_STRING");
	private static final String tableName = System.getenv("TABLE_STORAGE_TABLE_NAME");

	private static final String storageConnString = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
	private static final String blobContainerName = System.getenv("BLOB_CONTAINER_NAME");



	private static InternalPspApi pspApi = null;
	private static TableServiceClient tableServiceClient = null;

	private static BlobContainerClient blobContainerClient;

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
			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(fdrNewApiKey);
			pspApi = new InternalPspApi(apiClient);
			pspApi.setCustomBaseUrl(fdrNewBaseUrl);
		}
		return pspApi;
	}

	private static TableServiceClient getTableServiceClient(){
		if(tableServiceClient==null){
			tableServiceClient = new TableServiceClientBuilder()
					.connectionString(tableStorageConnString)
					.buildClient();
			tableServiceClient.createTableIfNotExists(tableName);
		}
		return tableServiceClient;
	}

	private static BlobContainerClient getBlobContainerClient(){
		if(blobContainerClient==null){
			BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(storageConnString).buildClient();
			blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(blobContainerName);
		}
		return blobContainerClient;
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

		String fdrBk = null;
		String pspIdBk = null;
		try {
			// read xml
			Document document = loadXMLString(content);
			Element element = searchNodeByName(document, NODO_INVIA_FLUSSO_RENDICONTAZIONE);
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = getInstanceByNode(element, NodoInviaFlussoRendicontazioneRequest.class);
			CtFlussoRiversamento ctFlussoRiversamento = getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);

			// extract pathparam for FDR
			String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
			fdrBk = fdr;
			String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();
			pspIdBk = pspId;

			logger.info("Process fdr=["+fdr+"], pspId=["+pspId+"]");

			// create body for create FDR
			CreateRequest createRequest = getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);
			// call create FDR
			logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_CREATE.name());
			manageHttpError(logger, HttpEventTypeEnum.INTERNAL_CREATE, fileName, fdr, pspId, () ->
				getPspApi().internalCreate(fdr, pspId, createRequest)
			);

			// create body for addPayment FDR (partitioned)
			List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
			logger.info("Tot CtDatiSingoliPagamenti ["+datiSingoliPagamenti.size()+"]");
			int partitionSize = Integer.parseInt(addPaymentRequestPartitionSize);
			List<AddPaymentRequest> addPaymentRequestList = getAddPaymentRequestListPartioned(datiSingoliPagamenti, partitionSize);
			logger.info("Tot AddPaymentRequest=["+addPaymentRequestList.size()+"], partioned by ["+partitionSize+"]");
			// call addPayment FDR for every patition
			for(AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
				logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_ADD_PAYMENT.name()+" (partition=" + addPaymentRequestList.indexOf(addPaymentRequest) + 1 + ", tot payments=" + addPaymentRequest.getPayments().size() + ")");
				manageHttpError(logger, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, fileName, fdr, pspId, () ->
					getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest)
				);
			}

			// call publish FDR
			logger.info("Calling... "+HttpEventTypeEnum.INTERNAL_PUBLISH.name());
			manageHttpError(logger, HttpEventTypeEnum.INTERNAL_PUBLISH, fileName, fdr, pspId, () ->
				getPspApi().internalPublish(fdr, pspId)
			);

			// delete BLOB
			logger.info("Deleting... blob file "+fileName);
			try{
				getBlobContainerClient().getBlobClient(fileName).delete();
			} catch (Exception e) {
				Instant now = Instant.now();
				ErrorEnum error = ErrorEnum.DELETE_BLOB_ERROR;
				String message = getErrorMessage(error, fileName, now);
				logger.log(Level.SEVERE, message, e);
				sendGenericError(logger, now, fileName, fdr, pspId, error, e);
				throw new AppException(message, e);
			}

			logger.info("Done processing events");
		} catch (AppException e) {
			logger.info("Failure processing events");
		} catch (Exception e) {
			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.GENERIC_ERROR;
			String message = getErrorMessage(error, fileName, now);
			logger.log(Level.SEVERE, message, e);
			sendGenericError(logger, now, fileName, fdrBk, pspIdBk, error, e);
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

	private static String getHttpErrorMessage(ErrorEnum errorEnum, String fileName, HttpEventTypeEnum httpEventTypeEnum, String errorCode, Instant now){
		return "[ALERT] [error="+errorEnum.name()+"] [fileName"+fileName+"] [httpEventTypeEnum"+httpEventTypeEnum.name()+"] [errorCode="+errorCode+"] Http error at "+ now;
	}
	private static String getErrorMessage(ErrorEnum errorEnum, String fileName, Instant now){
		return "[ALERT] [error="+errorEnum.name()+"] [fileName"+fileName+"] Http error at "+ now;
	}
	private static<T> void manageHttpError(Logger logger, HttpEventTypeEnum httpEventTypeEnum, String fileName, String fdr, String pspId, SupplierWithApiException<T> fn){
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
			ErrorEnum error = ErrorEnum.HTTP_ERROR;
			String message = getHttpErrorMessage(error, fileName, httpEventTypeEnum, errorCode, now);
			logger.log(Level.SEVERE, message, e);
			sendHttpError(logger, now, fileName, fdr, pspId, error, httpEventTypeEnum, errorResposne, errorCode, e);
			throw new AppException(message, e);
		}
	}

	private static void sendGenericError(Logger logger, Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Exception e){
		_sendToErrorTable(logger, now, fileName, fdr, pspId, errorEnum, Optional.empty(),Optional.empty(), Optional.empty(), e);
	}
	private static void sendHttpError(Logger logger, Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String httpErrorResposne, String httpErrorCode, Exception e){
		_sendToErrorTable(logger, now, fileName, fdr, pspId, errorEnum, Optional.of(httpEventTypeEnum), Optional.of(httpErrorResposne), Optional.of(httpErrorCode), e);
	}
	private static void _sendToErrorTable(Logger logger, Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Optional<HttpEventTypeEnum> httpEventTypeEnum, Optional<String> httpErrorResposne, Optional<String> httpErrorCode, Exception e){
		String id = UUID.randomUUID().toString();
		Map<String,Object> errorMap = new LinkedHashMap<>();
		errorMap.put(AppConstant.columnFieldId, id);
		errorMap.put(AppConstant.columnFieldCreated, now);
		errorMap.put(AppConstant.columnFieldFileName, fileName);
		errorMap.put(AppConstant.columnFieldFdr, fdr);
		errorMap.put(AppConstant.columnFieldPspId, pspId);
		errorMap.put(AppConstant.columnFieldErrorType, errorEnum.name());
		httpEventTypeEnum.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpEventType, a.name()));
		httpErrorResposne.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResposne, a));
		httpErrorCode.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorCode, a));
		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));

		String partitionKey =  now.toString().substring(0,10);

		logger.info("Send to "+tableName+" record with "+AppConstant.columnFieldId+"="+id);
		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
		TableEntity entity = new TableEntity(partitionKey, id);
		entity.setProperties(errorMap);
		tableClient.createEntity(entity);
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

