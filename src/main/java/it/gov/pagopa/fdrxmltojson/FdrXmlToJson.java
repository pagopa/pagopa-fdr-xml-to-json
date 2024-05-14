package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.google.common.collect.Lists;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {

	private static final Integer MAX_RETRY_COUNT = 10;
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
		payStatusMap.put("4", PaymentStatusEnum.STAND_IN);
		payStatusMap.put("8", PaymentStatusEnum.STAND_IN_NO_RPT);
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

	/*
	Executing this Azure Function in exponential retry, with steps:
	- retry 0: 0
	- retry 1: 10s
	- retry 2: 20s
	- retry 3: 40s
	- ...
	 */
    @FunctionName("BlobFdrXmlToJsonEventProcessor")
	@ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
    public void processNodoReEvent (
			@BlobTrigger(
					name = "xmlTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "xmlsharefile/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			final ExecutionContext context) {

		String errorCause = null;
		boolean isPersistenceOk = true;
		Throwable causedBy = null;
		int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();

		Logger logger = context.getLogger();
		if (retryIndex == MAX_RETRY_COUNT) {
			logger.log(Level.WARNING, () -> String.format("[ALERT][FdrXmlToJson][LAST_RETRY] Performing last retry for blob processing: InvocationId [%s], File name: %s", context.getInvocationId(), fileName));
		}

		String fdrBk = null;
		String pspIdBk = null;
		try {
			logger.log(Level.INFO, () -> String.format("Performing blob processing: InvocationId [%s], Retry Attempt [%d], File name: [%s]", context.getInvocationId(), retryIndex, fileName));

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

			// create body for create FDR
			CreateRequest createRequest = getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

			// call create FDR
			manageHttpError(HttpEventTypeEnum.INTERNAL_CREATE, fileName, fdr, pspId, () ->
				getPspApi().internalCreate(fdr, pspId, createRequest)
			);

			// create body for addPayment FDR (partitioned)
			List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
			int partitionSize = Integer.parseInt(addPaymentRequestPartitionSize);
			List<AddPaymentRequest> addPaymentRequestList = getAddPaymentRequestListPartioned(datiSingoliPagamenti, partitionSize);
			// call addPayment FDR for every partition
			for(AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
				manageHttpError(HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, fileName, fdr, pspId, () ->
					getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest)
				);
			}

			// call publish FDR
			manageHttpError(HttpEventTypeEnum.INTERNAL_PUBLISH, fileName, fdr, pspId, () ->
				getPspApi().internalPublish(fdr, pspId)
			);

			// delete BLOB
			try {
				getBlobContainerClient().getBlobClient(fileName).delete();
			} catch (Exception e) {
				Instant now = Instant.now();
				ErrorEnum error = ErrorEnum.DELETE_BLOB_ERROR;
				sendGenericError(now, fileName, fdr, pspId, error, e);

				isPersistenceOk = false;
				errorCause = getErrorMessage(error, fileName, now);
				causedBy = e;
			}

		} catch (AppException e) {
			isPersistenceOk = false;
			errorCause = e.getMessage();
			causedBy = e;

		} catch (Exception e) {
			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.GENERIC_ERROR;
			sendGenericError(now, fileName, fdrBk, pspIdBk, error, e);

			isPersistenceOk = false;
			errorCause = getErrorMessage(error, fileName, now);
			causedBy = e;
        }

		if (!isPersistenceOk) {
			String finalErrorCause = errorCause;
			logger.log(Level.SEVERE, () -> finalErrorCause);
			throw new AppException(errorCause, causedBy);
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
		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"] [fileName="+fileName+"] [httpEventTypeEnum="+httpEventTypeEnum.name()+"] [errorCode="+errorCode+"] Http error at "+ now;
	}
	private static String getErrorMessage(ErrorEnum errorEnum, String fileName, Instant now){
		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"] [fileName="+fileName+"] Http error at "+ now;
	}
	private static<T> void manageHttpError(HttpEventTypeEnum httpEventTypeEnum, String fileName, String fdr, String pspId, SupplierWithApiException<T> fn){
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
			sendHttpError(now, fileName, fdr, pspId, error, httpEventTypeEnum, errorResposne, errorCode, e);
			throw new AppException(message, e);
		}
	}

	private static void sendGenericError(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Exception e){
		_sendToErrorTable(now, fileName, fdr, pspId, errorEnum, Optional.empty(),Optional.empty(), Optional.empty(), e);
	}
	private static void sendHttpError(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String httpErrorResposne, String httpErrorCode, Exception e){
		_sendToErrorTable(now, fileName, fdr, pspId, errorEnum, Optional.ofNullable(httpEventTypeEnum), Optional.ofNullable(httpErrorResposne), Optional.of(httpErrorCode), e);
	}
	private static void _sendToErrorTable(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Optional<HttpEventTypeEnum> httpEventTypeEnum, Optional<String> httpErrorResposne, Optional<String> httpErrorCode, Exception e){
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

		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
		TableEntity entity = new TableEntity(partitionKey, id);
		entity.setProperties(errorMap);
		tableClient.createEntity(entity);
	}

	private List<AddPaymentRequest> getAddPaymentRequestListPartioned(List<CtDatiSingoliPagamenti> datiSingoliPagamentiList, int size){
		List<List<CtDatiSingoliPagamenti>> datiSingoliPagamentiPartitioned = Lists.partition(datiSingoliPagamentiList, size);
		AtomicInteger index = new AtomicInteger(1);
		return datiSingoliPagamentiPartitioned.stream()
				.map(datiSingoliPagamentiListPartion -> {
					AddPaymentRequest addPaymentRequest = new AddPaymentRequest();
					addPaymentRequest.setPayments(datiSingoliPagamentiListPartion
							.stream()
							.map(v -> getPayment(v, index.getAndIncrement()))
							.collect(Collectors.toList()));
					return addPaymentRequest;
				})
				.collect(Collectors.toList());
	}
	private Payment getPayment(CtDatiSingoliPagamenti ctDatiSingoliPagamenti, int index){
		Payment payment = new Payment();
		payment.setIndex((long) index);
		payment.setIdTransfer(ctDatiSingoliPagamenti.getIndiceDatiSingoloPagamento().longValue());
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
		Element elementFound = null;
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node currentode = nodeList.item(i);
			if (currentode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) currentode;
				if(element.getTagName().endsWith(elementToSearch)){
					return element;
				} else {
					elementFound = searchNodeByName(element, elementToSearch);
					if(elementFound!=null){
						return elementFound;
					}
				}
			}
		}
		return null;
	}
}

