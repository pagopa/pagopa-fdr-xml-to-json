package it.gov.pagopa.fdrxmltojson;


import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.pagopa.fdrxmltojson.util.FdR3ClientUtil;
import it.gov.pagopa.fdrxmltojson.util.GZipUtil;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class FdrXmlCommon {

	private static final Integer MAX_RETRY_COUNT = 10;
	private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";

	private static final String PAYMENT_CHUNK_SIZE = System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE");

	private static Logger logger;



	public void convertXmlToJson(ExecutionContext context,
								 String sessionId,
								 byte[] content,
								 String fileName) {
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
			logger.log(Level.INFO, () -> messageFormat(sessionId, context.getInvocationId(), null, fileName, "Performing blob processing [retry attempt %d]", retryIndex));

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

				// call create FDR flow
				createFdRFlow(sessionId, context.getInvocationId(), fileName, fdr, pspId, nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

				// call add FDR payments
				addFdRPayments(sessionId, context.getInvocationId(), fileName, fdr, pspId, ctFlussoRiversamento);

				// call publish FdR flow
				publishFdRFlow(sessionId, context.getInvocationId(), fileName, fdr, pspId);
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

	private Document loadXML(InputStream inputStream) throws Exception{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		return db.parse(new InputSource(inputStream));
	}

	private Element searchNodeByName(Node node, String elementToSearch) {
		NodeList nodeList = node.getChildNodes();
		Element elementFound = null;
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node currentNode = nodeList.item(i);
			if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) currentNode;
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

	private <T> T getInstanceByNode(Node node, Class<T> type) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(type);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<T> jaxb =  unmarshaller.unmarshal(node, type);
		return jaxb.getValue();
	}

	private <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(type);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
		return jaxb.getValue();
	}

	private enum HttpEventTypeEnum {
		INTERNAL_CREATE,
		INTERNAL_ADD_PAYMENT,
		INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
		INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
		INTERNAL_PUBLISH;
	}

	private void createFdRFlow(
			String sessionId, String invocationId, String fileName,
			String fdr, String pspId,
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest,
			CtFlussoRiversamento ctFlussoRiversamento) throws IOException {

		String operation = "Create request";
		logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, operation));
		try {
			FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
			// create body for create FDR
			CreateRequest createRequest = fdR3ClientUtil.getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);
			fdR3ClientUtil.getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

			FdR3ClientUtil.getPspApi().internalCreate(fdr, pspId, createRequest);
		} catch (ApiException e) {
			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
				String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
				if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_FLOW_ALREADY_CREATED)) {
					// error != FDR-3002
					// save on table storage and send alert
					logger.log(Level.SEVERE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s error [appErrorCode: %s]", operation, appErrorCode));

					generateAlertAndSaveOnTable(
							sessionId, invocationId, pspId, fdr, fileName,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
							appErrorCode, e
					);
				}
			}
			else {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
						String.valueOf(e.getCode()), e
				);
			}
		}
	}


	private void addFdRPayments(String sessionId, String invocationId, String fileName,
								String fdr, String pspId, CtFlussoRiversamento ctFlussoRiversamento) throws IOException {
		List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
		int chunkSize = Integer.parseInt(PAYMENT_CHUNK_SIZE);
		FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
		List<AddPaymentRequest> addPaymentRequestList = fdR3ClientUtil.getAddPaymentRequestListChunked(datiSingoliPagamenti, chunkSize);
		// call addPayment FDR for each partition
		for (AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
			sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, 0);
		}
	}

	private void sendAddFdrPayments(String sessionId, String invocationId, String fileName,
									String fdr, String pspId, AddPaymentRequest addPaymentRequest, int retry) throws IOException {
		String operation = String.format("Add payments request [retry: %d]", retry);

		try {
			int chunkSize = addPaymentRequest.getPayments().size();
			logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s [chunk size: %d]", operation, chunkSize));
			FdR3ClientUtil.getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
		}
		catch (ApiException e) {
			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
				String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
				if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_PAYMENT_ALREADY_ADDED)) {
					generateAlertAndSaveOnTable(
							sessionId, invocationId, pspId, fdr, fileName,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
							appErrorCode, e
					);
				}
				else {
					// case FDR_PAYMENT_ALREADY_ADDED
					if (retry < 2) {
						ErrorResponse errorResponse = ErrorResponse.fromJson(e.getResponseBody());
						if (!errorResponse.getErrors().isEmpty()) {
							String path = errorResponse.getErrors().get(0).getPath();
							List<Long> indexes = Arrays.stream(path.replaceAll("[\\[\\] ]", "").split(","))
									.map(Long::parseLong).toList();
							List<Payment> paymentsFiltered = addPaymentRequest.getPayments().stream().filter(payment -> !indexes.contains(payment.getIndex())).toList();
							if (!paymentsFiltered.isEmpty()) {
								addPaymentRequest.setPayments(paymentsFiltered);

								logger.log(Level.FINE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s try to send chunk without conflicting indexes", operation));
								sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, retry + 1);
							} else {
								logger.log(Level.FINE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s already added. Nothing to retry", operation));
							}
						} else {
							generateAlertAndSaveOnTable(
									sessionId, invocationId, pspId, fdr, fileName,
									ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
									appErrorCode, e
							);
						}
					}
					else {
						generateAlertAndSaveOnTable(
								sessionId, invocationId, pspId, fdr, fileName,
								ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
								appErrorCode, e
						);
					}
				}
			}
			else {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
						String.valueOf(e.getCode()), e
				);
			}
		}
	}

	private void publishFdRFlow(String sessionId, String invocationId, String fileName, String fdr, String pspId) throws IOException {
		String operation = "Publish request";
		logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, operation));
		try {
			FdR3ClientUtil.getPspApi().internalPublish(fdr, pspId);
		} catch (ApiException e) {
			if (e.getCode() != HttpStatus.NOT_FOUND.value()) {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_PUBLISH,
						String.valueOf(e.getCode()), e
				);
			}
		}
	}

	private void generateAlertAndSaveOnTable(String sessionId, String invocationId, String pspId, String fdr, String fileName, ErrorEnum error, HttpEventTypeEnum httpEventTypeEnum, String errorCode, ApiException e) {
		Instant now = Instant.now();
		String message = messageFormat(sessionId, invocationId, pspId, fileName, getHttpErrorMessage(error, httpEventTypeEnum, errorCode, now));
		sendHttpError(now, fileName, fdr, pspId, error, httpEventTypeEnum, e.getResponseBody(), errorCode, e);
		throw new AppException(message, e);
	}

	private String messageFormat(String sessionId, String invocationId, String pspId, String fileName, String message, Object... args) {
		String suffix = String.format(" [sessionId: %s][invocationId: %s][psp: %s][filename: %s]", sessionId, invocationId, pspId, fileName);
		return String.format(message, args) + suffix;
	}

	private static String getHttpErrorMessage(ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String errorCode, Instant now){
		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"][httpEventTypeEnum="+httpEventTypeEnum.name()+"][errorCode="+errorCode+"] Http error at "+ now;
	}

	private static String getErrorMessage(ErrorEnum errorEnum, String fileName, Instant now){
		return "[ALERT][FdrXmlToJson]["+errorEnum.name()+"] [fileName="+fileName+"] Http error at "+ now;
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

//		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
//		TableEntity entity = new TableEntity(partitionKey, id);
//		entity.setProperties(errorMap);
//		tableClient.createEntity(entity);
	}

}

