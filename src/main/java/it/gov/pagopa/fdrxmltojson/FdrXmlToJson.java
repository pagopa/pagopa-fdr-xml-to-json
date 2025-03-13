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
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivoco;
import it.gov.pagopa.fdrxmltojson.util.GZipUtil;
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
import java.io.InputStream;
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

	private static Logger logger;

	private static InternalPspApi pspApi = null;
	private static TableServiceClient tableServiceClient = null;

	private static BlobContainerClient blobContainerClient;

//	private static final Map<StTipoIdentificativoUnivoco, SenderTypeEnum> typeMap = new LinkedHashMap<>();
//	private static final Map<String, PaymentStatusEnum> payStatusMap = new LinkedHashMap<>();
//	static {
//		typeMap.put(StTipoIdentificativoUnivoco.G, SenderTypeEnum.LEGAL_PERSON);
//		typeMap.put(StTipoIdentificativoUnivoco.A, SenderTypeEnum.ABI_CODE);
//		typeMap.put(StTipoIdentificativoUnivoco.B, SenderTypeEnum.BIC_CODE);
//
//		payStatusMap.put("0", PaymentStatusEnum.EXECUTED);
//		payStatusMap.put("3", PaymentStatusEnum.REVOKED);
//		payStatusMap.put("4", PaymentStatusEnum.STAND_IN);
//		payStatusMap.put("8", PaymentStatusEnum.STAND_IN_NO_RPT);
//		payStatusMap.put("9", PaymentStatusEnum.NO_RPT);
//	}

//	private static InternalPspApi getPspApi() {
//		if(pspApi==null){
//			ApiClient apiClient = new ApiClient();
//			apiClient.setApiKey(fdrNewApiKey);
//			pspApi = new InternalPspApi(apiClient);
//			pspApi.setCustomBaseUrl(fdrNewBaseUrl);
//		}
//		return pspApi;
//	}

//	private static TableServiceClient getTableServiceClient(){
//		if(tableServiceClient==null){
//			tableServiceClient = new TableServiceClientBuilder()
//					.connectionString(tableStorageConnString)
//					.buildClient();
//			tableServiceClient.createTableIfNotExists(tableName);
//		}
//		return tableServiceClient;
//	}

//	private static BlobContainerClient getBlobContainerClient(){
//		if(blobContainerClient==null){
//			BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(storageConnString).buildClient();
//			blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(blobContainerName);
//		}
//		return blobContainerClient;
//	}

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
					path = "%BLOB_CONTAINER_NAME%/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			@BindingName("Metadata") Map<String, String> blobMetadata,
			final ExecutionContext context) {

		if (Boolean.parseBoolean(blobMetadata.getOrDefault("elaborate", "false"))) {

			String sessionId = blobMetadata.getOrDefault("sessionId", "NA");
			FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();

			fdrXmlCommon.convertXmlToJson(context, sessionId, content, fileName);

			/**
			String errorCause = null;
			boolean isPersistenceOk = true;
			Throwable causedBy = null;
			int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();

			logger = context.getLogger();
			if (retryIndex == MAX_RETRY_COUNT) {
				logger.log(Level.WARNING, () -> String.format("[ALERT][FdrXmlToJson][LAST_RETRY] Performing last retry for blob processing: SessionId [%s], InvocationId [%s], File name: %s",  sessionId, context.getInvocationId(), fileName));
			}

			String fdrBk = null;
			String pspIdBk = null;

			try {
				logger.log(Level.INFO, () -> String.format("Performing blob processing: SessionId [%s], InvocationId [%s], Retry Attempt [%d], File name: [%s]", sessionId, context.getInvocationId(), retryIndex, fileName));

				if (GZipUtil.isGzip(content)) {

					// decompress GZip file
					InputStream decompressedStream = GZipUtil.decompressGzip(content);

					// read xml
					Document document = loadXML(decompressedStream);
					Element element = searchNodeByName(document, NODO_INVIA_FLUSSO_RENDICONTAZIONE);

					NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = getInstanceByNode(element, NodoInviaFlussoRendicontazioneRequest.class);
					CtFlussoRiversamento ctFlussoRiversamento = getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);

					// extract pathParam for FdR
					String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
					fdrBk = fdr;
					String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();
					pspIdBk = pspId;

					// create body for create FDR
					CreateRequest createRequest = getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

					// call create FDR flow
					createFdRFlow(sessionId, context.getInvocationId(), fileName, fdr, pspId, createRequest);

					// call add FDR payments
					addFdRPayments(sessionId, context.getInvocationId(), fileName, fdr, pspId, ctFlussoRiversamento);

					// call publish FdR flow
					publishFdRFlow(sessionId, context.getInvocationId(), fdr, pspId);
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
			 */
		}
    }

//	private Document loadXML(InputStream inputStream) throws Exception{
//		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
//		DocumentBuilder db = dbf.newDocumentBuilder();
//		return db.parse(new InputSource(inputStream));
//	}

//	private Element searchNodeByName(Node node, String elementToSearch) {
//		NodeList nodeList = node.getChildNodes();
//		Element elementFound = null;
//		for (int i = 0; i < nodeList.getLength(); i++) {
//			Node currentNode = nodeList.item(i);
//			if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
//				Element element = (Element) currentNode;
//				if(element.getTagName().endsWith(elementToSearch)){
//					return element;
//				} else {
//					elementFound = searchNodeByName(element, elementToSearch);
//					if(elementFound!=null){
//						return elementFound;
//					}
//				}
//			}
//		}
//		return null;
//	}

	private enum HttpEventTypeEnum {
		INTERNAL_CREATE,
		INTERNAL_ADD_PAYMENT,
		INTERNAL_PUBLISH;
	}

	@FunctionalInterface
	public interface SupplierWithApiException<T> {
		T get() throws ApiException;
	}

//	private void createFdRFlow(
//			String sessionId, String invocationId, String fileName,
//			String fdr, String pspId, CreateRequest createRequest) throws IOException {
//		String operation = "Create FdR flow request";
//		logger.log(Level.INFO, () ->
//				String.format("%s: SessionId [%s], InvocationId [%s], PSP [%s], FileName: [%s]",
//						operation, sessionId, invocationId, pspId, fileName));
//		try {
//			getPspApi().internalCreate(fdr, pspId, createRequest);
//		} catch (ApiException e) {
//			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
//				String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
//				if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_FLOW_ALREADY_CREATED)) {
//					logger.log(Level.SEVERE, () ->
//							String.format("%s error: SessionId [%s], InvocationId [%s], StatusCode [%s], ResponseBody: [%s]",
//									operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//					Instant now = Instant.now();
//					ErrorEnum error = ErrorEnum.HTTP_ERROR;
//					String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_CREATE, appErrorCode, now);
//					sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_CREATE, e.getResponseBody(), appErrorCode, e);
//					throw new AppException(message, e);
//				}
//			}
//			else {
//				Instant now = Instant.now();
//				ErrorEnum error = ErrorEnum.HTTP_ERROR;
//				String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_CREATE, String.valueOf(e.getCode()), now);
//				sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_CREATE, e.getResponseBody(), String.valueOf(e.getCode()), e);
//				throw new AppException(message, e);
//			}
//		}
//	}

//	private void addFdRPayments(String sessionId, String invocationId, String fileName,
//								String fdr, String pspId, CtFlussoRiversamento ctFlussoRiversamento) throws IOException {
//		List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
//		int chunkSize = Integer.parseInt(addPaymentRequestPartitionSize);
//		List<AddPaymentRequest> addPaymentRequestList = getAddPaymentRequestListChunked(datiSingoliPagamenti, chunkSize);
//		// call addPayment FDR for each partition
//		for (AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
//			sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, 0);
//		}
//	}

//	private void sendAddFdrPayments(String sessionId, String invocationId, String fileName,
//									 String fdr, String pspId, AddPaymentRequest addPaymentRequest, int retry) throws IOException {
//		String operation = String.format("Add Fdr payments request [retry: %d]", retry);
//
//		try {
//			int chunkSize = addPaymentRequest.getPayments().size();
//			logger.log(Level.INFO, () ->
//					String.format("%s. Chunk size: %d : SessionId [%s], InvocationId [%s], PSP [%s], FileName: [%s]",
//							operation, chunkSize, sessionId, invocationId, pspId, fileName));
//			getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
//		}
//		catch (ApiException e) {
//			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
//				String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
//				if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_PAYMENT_ALREADY_ADDED)) {
//					logger.log(Level.SEVERE, () ->
//							String.format("%s error: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//									operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//					Instant now = Instant.now();
//					ErrorEnum error = ErrorEnum.HTTP_ERROR;
//					String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, appErrorCode, now);
//					sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, e.getResponseBody(), appErrorCode, e);
//					throw new AppException(message, e);
//				}
//				else {
//					// case FDR_PAYMENT_ALREADY_ADDED
//					if (retry < 2) {
//						ErrorResponse errorResponse = ErrorResponse.fromJson(e.getResponseBody());
//						if (!errorResponse.getErrors().isEmpty()) {
//							String path = errorResponse.getErrors().get(0).getPath();
//							List<Long> indexes = Arrays.stream(path.replaceAll("[\\[\\] ]", "").split(","))
//									.map(Long::parseLong).toList();
//							List<Payment> paymentsFiltered = addPaymentRequest.getPayments().stream().filter(payment -> !indexes.contains(payment.getIndex())).toList();
//							if (!paymentsFiltered.isEmpty()) {
//								addPaymentRequest.setPayments(paymentsFiltered);
//								logger.log(Level.FINE, () ->
//										String.format("%s try to send chunk without conflicting indexes: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//												operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//								sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, retry + 1);
//							} else {
//								logger.log(Level.FINE, () ->
//										String.format("%s already added. Nothing to retry: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//												operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//							}
//						} else {
//							logger.log(Level.SEVERE, () ->
//									String.format("%s no error in the response: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//											operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//							Instant now = Instant.now();
//							ErrorEnum error = ErrorEnum.HTTP_ERROR;
//							String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, appErrorCode, now);
//							sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, e.getResponseBody(), appErrorCode, e);
//							throw new AppException(message, e);
//						}
//					}
//					else {
//						logger.log(Level.SEVERE, () ->
//								String.format("%s no error in the response: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//										operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//						Instant now = Instant.now();
//						ErrorEnum error = ErrorEnum.HTTP_ERROR;
//						String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, appErrorCode, now);
//						sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, e.getResponseBody(), appErrorCode, e);
//						throw new AppException(message, e);
//					}
//				}
//			}
//			else {
//				Instant now = Instant.now();
//				ErrorEnum error = ErrorEnum.HTTP_ERROR;
//				String message = getHttpErrorMessage(error, fileName, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, String.valueOf(e.getCode()), now);
//				sendHttpError(now, fileName, fdr, pspId, error, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT, e.getResponseBody(), String.valueOf(e.getCode()), e);
//				throw new AppException(message, e);
//			}
//		}
//	}

//	private void publishFdRFlow(String sessionId, String invocationId, String fdr, String pspId) throws IOException {
//		String operation = "Publish FdR flow request";
//		logger.log(Level.INFO, () ->
//				String.format("%s: SessionId [%s], InvocationId [%s], PSP [%s]",
//						operation, sessionId, invocationId, pspId));
//		try {
//			getPspApi().internalPublish(fdr, pspId);
//		} catch (ApiException e) {
//			if (e.getCode() != HttpStatus.NOT_FOUND.value()) {
//				logger.log(Level.SEVERE, () ->
//						String.format("%s error: SessionId [%s], InvocationId [%s], Status code [%d], Body: [%s]",
//								operation, sessionId, invocationId, e.getCode(), e.getResponseBody()));
//			}
//		}
//
//	}

//	private static String getHttpErrorMessage(ErrorEnum errorEnum, String fileName, HttpEventTypeEnum httpEventTypeEnum, String errorCode, Instant now){
//		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"] [fileName="+fileName+"] [httpEventTypeEnum="+httpEventTypeEnum.name()+"] [errorCode="+errorCode+"] Http error at "+ now;
//	}
//
//	private static String getErrorMessage(ErrorEnum errorEnum, String fileName, Instant now){
//		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"] [fileName="+fileName+"] Http error at "+ now;
//	}

//	private static void sendGenericError(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Exception e){
//		logger.log(Level.SEVERE, () -> String.format("***** TODO sendGenericError"));
////	TODO	_sendToErrorTable(now, fileName, fdr, pspId, errorEnum, Optional.empty(),Optional.empty(), Optional.empty(), e);
//	}
//	private static void sendHttpError(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String httpErrorResposne, String httpErrorCode, Exception e){
//		logger.log(Level.SEVERE, () -> String.format("***** TODO sendHttpError"));
////	TODO	_sendToErrorTable(now, fileName, fdr, pspId, errorEnum, Optional.ofNullable(httpEventTypeEnum), Optional.ofNullable(httpErrorResposne), Optional.of(httpErrorCode), e);
//	}
//	private static void _sendToErrorTable(Instant now, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Optional<HttpEventTypeEnum> httpEventTypeEnum, Optional<String> httpErrorResposne, Optional<String> httpErrorCode, Exception e){
//		String id = UUID.randomUUID().toString();
//		Map<String,Object> errorMap = new LinkedHashMap<>();
//		errorMap.put(AppConstant.columnFieldId, id);
//		errorMap.put(AppConstant.columnFieldCreated, now);
//		errorMap.put(AppConstant.columnFieldFileName, fileName);
//		errorMap.put(AppConstant.columnFieldFdr, fdr);
//		errorMap.put(AppConstant.columnFieldPspId, pspId);
//		errorMap.put(AppConstant.columnFieldErrorType, errorEnum.name());
//		httpEventTypeEnum.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpEventType, a.name()));
//		httpErrorResposne.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResposne, a));
//		httpErrorCode.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorCode, a));
//		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));
//
//		String partitionKey =  now.toString().substring(0,10);
//
//		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
//		TableEntity entity = new TableEntity(partitionKey, id);
//		entity.setProperties(errorMap);
//		tableClient.createEntity(entity);
//	}

//	private List<AddPaymentRequest> getAddPaymentRequestListChunked(List<CtDatiSingoliPagamenti> datiSingoliPagamentiList, int size){
//		List<List<CtDatiSingoliPagamenti>> datiSingoliPagamentiPartitioned = Lists.partition(datiSingoliPagamentiList, size);
//		AtomicInteger index = new AtomicInteger(1);
//		return datiSingoliPagamentiPartitioned.stream()
//				.map(datiSingoliPagamentiListPartion -> {
//					AddPaymentRequest addPaymentRequest = new AddPaymentRequest();
//					addPaymentRequest.setPayments(datiSingoliPagamentiListPartion
//							.stream()
//							.map(v -> getPayment(v, index.getAndIncrement()))
//							.collect(Collectors.toList()));
//					return addPaymentRequest;
//				})
//				.collect(Collectors.toList());
//	}

//	private Payment getPayment(CtDatiSingoliPagamenti ctDatiSingoliPagamenti, int index){
//		Payment payment = new Payment();
//		payment.setIndex((long) index);
//		payment.setIdTransfer(ctDatiSingoliPagamenti.getIndiceDatiSingoloPagamento().longValue());
//		payment.setIuv(ctDatiSingoliPagamenti.getIdentificativoUnivocoVersamento());
//		payment.setIur(ctDatiSingoliPagamenti.getIdentificativoUnivocoRiscossione());
//		payment.setPay(ctDatiSingoliPagamenti.getSingoloImportoPagato().doubleValue());
//		payment.setPayDate(ctDatiSingoliPagamenti.getDataEsitoSingoloPagamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
//		payment.setPayStatus(payStatusMap.get(ctDatiSingoliPagamenti.getCodiceEsitoSingoloPagamento()));
//		return payment;
//	}

//	private CreateRequest getCreateRequest(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
//		CreateRequest createRequest = new CreateRequest();
//		createRequest.setFdr(nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso());
//		createRequest.setFdrDate(nodoInviaFlussoRendicontazioneRequest.getDataOraFlusso().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
//		createRequest.setSender(getSender(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
//		createRequest.setReceiver(getReceiver(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento));
//		createRequest.setRegulation(ctFlussoRiversamento.getIdentificativoUnivocoRegolamento());
//		createRequest.setRegulationDate(ctFlussoRiversamento.getDataRegolamento().toGregorianCalendar().toZonedDateTime().toOffsetDateTime());
//		createRequest.setBicCodePouringBank(ctFlussoRiversamento.getCodiceBicBancaDiRiversamento());
//		createRequest.setTotPayments(ctFlussoRiversamento.getNumeroTotalePagamenti().longValue());
//		createRequest.setSumPayments(ctFlussoRiversamento.getImportoTotalePagamenti().doubleValue());
//		return createRequest;
//	}

//	private Sender getSender(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
//		StTipoIdentificativoUnivoco tipoIdentificativoUnivoco = ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getTipoIdentificativoUnivoco();
//
//		Sender sender = new Sender();
//		sender.setType(typeMap.get(tipoIdentificativoUnivoco));
//		sender.setId(ctFlussoRiversamento.getIstitutoMittente().getIdentificativoUnivocoMittente().getCodiceIdentificativoUnivoco());
//		sender.setPspId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP());
//		sender.setPspName(Optional.ofNullable(ctFlussoRiversamento.getIstitutoMittente().getDenominazioneMittente()).orElse(""));
//		sender.setPspBrokerId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoIntermediarioPSP());
//		sender.setChannelId(nodoInviaFlussoRendicontazioneRequest.getIdentificativoCanale());
//		sender.setPassword(nodoInviaFlussoRendicontazioneRequest.getPassword());
//		return sender;
//	}

//	private Receiver getReceiver(NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest, CtFlussoRiversamento ctFlussoRiversamento){
//		String organizationId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoDominio();
//		String organizationName = ctFlussoRiversamento.getIstitutoRicevente().getDenominazioneRicevente();
//
//		Receiver receiver = new Receiver();
//		receiver.setId(ctFlussoRiversamento.getIstitutoRicevente().getIdentificativoUnivocoRicevente().getCodiceIdentificativoUnivoco());
//		receiver.setOrganizationId(organizationId);
//		receiver.setOrganizationName(organizationName!=null?organizationName:organizationId);
//		return receiver;
//	}

//	private <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
//		JAXBContext jaxbContext = JAXBContext.newInstance(type);
//		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//		JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
//		return jaxb.getValue();
//	}

//	private <T> T getInstanceByNode(Node node, Class<T> type) throws JAXBException {
//		JAXBContext jaxbContext = JAXBContext.newInstance(type);
//		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//		JAXBElement<T> jaxb =  unmarshaller.unmarshal(node, type);
//		return jaxb.getValue();
//	}


}

