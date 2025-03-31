package it.gov.pagopa.fdrxmltojson;


import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.ErrorEnum;
import it.gov.pagopa.fdrxmltojson.util.*;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.gov.pagopa.fdrxmltojson.util.XMLUtil.messageFormat;


public class FdrXmlCommon {

	private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";

	private static Logger logger;

	public void convertXmlToJson(ExecutionContext context,
								 String sessionId,
								 byte[] content,
								 String fileName,
								 long retryAttempt) throws Exception {

		logger = context.getLogger();

		logger.log(Level.INFO, () -> messageFormat(sessionId, context.getInvocationId(), null, fileName, "Performing blob processing [retry attempt %d]", retryAttempt));

		if (GZipUtil.isGzip(content)) {

			// decompress GZip file
			InputStream decompressedStream = GZipUtil.decompressGzip(content);

			// read xml
			Document document = XMLUtil.loadXML(decompressedStream);
			Element element = XMLUtil.searchNodeByName(document, NODO_INVIA_FLUSSO_RENDICONTAZIONE);

			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = XMLUtil.getInstanceByNode(element, NodoInviaFlussoRendicontazioneRequest.class);
			CtFlussoRiversamento ctFlussoRiversamento = XMLUtil.getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);

			// extract pathParam for FdR
			String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
			String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();

			// call create FDR flow
			createFdRFlow(sessionId, context.getInvocationId(), fileName, fdr, pspId, nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento, retryAttempt);

			// call add FDR payments
			addFdRPayments(sessionId, context.getInvocationId(), fileName, fdr, pspId, ctFlussoRiversamento, retryAttempt);

			// call publish FdR flow
			publishFdRFlow(sessionId, context.getInvocationId(), fileName, fdr, pspId, retryAttempt);
		}

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
			CtFlussoRiversamento ctFlussoRiversamento, long retryAttempt) throws IOException {

		String operation = "Create request";
		logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, operation));
		try {
			FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
			// create body for create FDR
			CreateRequest createRequest = fdR3ClientUtil.getCreateRequest(nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

			FdR3ClientUtil.getPspApi().internalCreate(fdr, pspId, createRequest);
		} catch (ApiException e) {
			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
				ErrorResponse errorBody = ErrorResponse.fromJson(e.getResponseBody());
				if (errorBody != null) {
					String appErrorCode = errorBody.getAppErrorCode();
					if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_FLOW_ALREADY_CREATED)) {
						// error != FDR-3002
						// save on table storage and send alert
						logger.log(Level.SEVERE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s error [appErrorCode: %s]", operation, Optional.ofNullable(appErrorCode).orElse("null")));

						generateAlertAndSaveOnTable(
								sessionId, invocationId, pspId, fdr, fileName,
								ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
								Optional.ofNullable(appErrorCode).orElse("null"), String.valueOf(retryAttempt), e
						);
					}
				}
				else {
					generateAlertAndSaveOnTable(
							sessionId, invocationId, pspId, fdr, fileName,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
							String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
					);
				}
			}
			else {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
						String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
				);
			}
		}
	}


	private void addFdRPayments(String sessionId, String invocationId, String fileName,
								String fdr, String pspId, CtFlussoRiversamento ctFlussoRiversamento, long retryAttempt) throws IOException {
		List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
		int chunkSize = getPaymentChunkSize();
		FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
		List<AddPaymentRequest> addPaymentRequestList = fdR3ClientUtil.getAddPaymentRequestListChunked(datiSingoliPagamenti, chunkSize);
		// call addPayment FDR for each partition
		for (AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
			sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, retryAttempt, 0);
		}
	}

	private void sendAddFdrPayments(String sessionId, String invocationId, String fileName,
									String fdr, String pspId, AddPaymentRequest addPaymentRequest, long retryAttempt, int internalRetry) throws IOException {
		String operation = String.format("Add payments request [retryAttempt: %d][internalRetry: %d]", retryAttempt, internalRetry);

		try {
			int chunkSize = addPaymentRequest.getPayments().size();
			logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s [chunk size: %d]", operation, chunkSize));
			FdR3ClientUtil.getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
		}
		catch (ApiException e) {
			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
				ErrorResponse errorBody = ErrorResponse.fromJson(e.getResponseBody());
				if (errorBody != null) {
					String appErrorCode = errorBody.getAppErrorCode();

					if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_PAYMENT_ALREADY_ADDED)) {
						generateAlertAndSaveOnTable(
								sessionId, invocationId, pspId, fdr, fileName,
								ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
								Optional.ofNullable(appErrorCode).orElse("NA"), String.format("%d-%d", retryAttempt, internalRetry), e
						);
					} else {
						// case FDR_PAYMENT_ALREADY_ADDED
						if (internalRetry < 2) {
							ErrorResponse errorResponse = ErrorResponse.fromJson(e.getResponseBody());
							if (errorResponse.getErrors() != null && !errorResponse.getErrors().isEmpty()) {
								String path = errorResponse.getErrors().get(0).getPath();
								if (path != null) {
									List<Long> indexes = Arrays.stream(path.replaceAll("[\\[\\] ]", "").split(","))
											.map(Long::parseLong).toList();
									List<Payment> paymentsFiltered = addPaymentRequest.getPayments().stream().filter(payment -> !indexes.contains(payment.getIndex())).toList();
									if (!paymentsFiltered.isEmpty()) {
										addPaymentRequest.setPayments(paymentsFiltered);

										logger.log(Level.FINE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s try to send chunk without conflicting indexes", operation));
										sendAddFdrPayments(sessionId, invocationId, fileName, fdr, pspId, addPaymentRequest, retryAttempt, internalRetry + 1);
									} else {
										logger.log(Level.FINE, () -> messageFormat(sessionId, invocationId, pspId, fileName, "%s already added. Nothing to internalRetry", operation));
									}
								} else {
									generateAlertAndSaveOnTable(
											sessionId, invocationId, pspId, fdr, fileName,
											ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
											appErrorCode, String.format("%d-%d. Path not found", retryAttempt, internalRetry), e
									);
								}
							} else {
								generateAlertAndSaveOnTable(
										sessionId, invocationId, pspId, fdr, fileName,
										ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
										appErrorCode, String.format("%d-%d", retryAttempt, internalRetry), e
								);
							}
						} else {
							generateAlertAndSaveOnTable(
									sessionId, invocationId, pspId, fdr, fileName,
									ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
									appErrorCode, String.format("%d-%d", retryAttempt, internalRetry), e
							);
						}
					}
				}
				else {
					generateAlertAndSaveOnTable(
							sessionId, invocationId, pspId, fdr, fileName,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
							String.valueOf(e.getCode()), String.format("%d-%d", retryAttempt, internalRetry), e
					);
				}
			}
			else {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
						String.valueOf(e.getCode()), String.format("%d-%d", retryAttempt, internalRetry), e
				);
			}
		}
	}

	private void publishFdRFlow(String sessionId, String invocationId, String fileName, String fdr, String pspId, long retryAttempt) {
		String operation = "Publish request";
		logger.log(Level.INFO, () -> messageFormat(sessionId, invocationId, pspId, fileName, operation));
		try {
			FdR3ClientUtil.getPspApi().internalPublish(fdr, pspId);
		} catch (ApiException e) {
			if (e.getCode() != HttpStatus.NOT_FOUND.value()) {
				generateAlertAndSaveOnTable(
						sessionId, invocationId, pspId, fdr, fileName,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_PUBLISH,
						String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
				);
			}
		}
	}

	private void generateAlertAndSaveOnTable(String sessionId, String invocationId, String pspId, String fdr, String fileName, ErrorEnum error, HttpEventTypeEnum httpEventTypeEnum, String errorCode, String retryAttempt, ApiException e) {
		Instant now = Instant.now();
		String message = messageFormat(sessionId, invocationId, pspId, fileName, getHttpErrorMessage(error, httpEventTypeEnum, errorCode, now));
		sendHttpError(now, sessionId, invocationId, fileName, fdr, pspId, error, httpEventTypeEnum, e.getResponseBody(), errorCode, retryAttempt, e);
		throw new AppException(message, e);
	}

	private static String getHttpErrorMessage(ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String errorCode, Instant now){
		return "[FdrXmlToJson]["+errorEnum.name()+"][httpEventTypeEnum="+httpEventTypeEnum.name()+"][errorCode="+errorCode+"] Http error at "+ now;
	}

	private static void sendHttpError(Instant now, String sessionId, String invocationId, String fileName, String fdr, String pspId, ErrorEnum errorEnum, HttpEventTypeEnum httpEventTypeEnum, String httpErrorResponse, String httpErrorCode, String retryAttempt, Exception e) {
		_sendToErrorTable(now, sessionId, invocationId, fileName, fdr, pspId, errorEnum, Optional.ofNullable(httpEventTypeEnum), Optional.ofNullable(httpErrorResponse), Optional.of(httpErrorCode), retryAttempt, e);
	}

	private static void _sendToErrorTable(Instant now, String sessionId, String invocationId, String fileName, String fdr, String pspId, ErrorEnum errorEnum, Optional<HttpEventTypeEnum> httpEventTypeEnum, Optional<String> httpErrorResponse, Optional<String> httpErrorCode, String retryAttempt, Exception e) {
		Map<String,Object> errorMap = new LinkedHashMap<>();
		errorMap.put(AppConstant.columnFieldSessionId, sessionId);
		errorMap.put(AppConstant.columnFieldCreated, now);
		errorMap.put(AppConstant.columnFieldFileName, fileName);
		errorMap.put(AppConstant.columnFieldFdr, fdr);
		errorMap.put(AppConstant.columnFieldPspId, pspId);
		errorMap.put(AppConstant.columnFieldErrorType, errorEnum.name());
		httpEventTypeEnum.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpEventType, a.name()));
		httpErrorResponse.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResponse, a));
		httpErrorCode.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorCode, a));
		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));
		errorMap.put(AppConstant.columnFieldRetryAttempt, retryAttempt);

		String partitionKey = LocalDate.ofInstant(now, TimeZone.getDefault().toZoneId()).toString();

		TableClient tableClient = StorageAccountUtil.getTableClient();
		TableEntity entity = new TableEntity(partitionKey, invocationId);
		entity.setProperties(errorMap);
		tableClient.createEntity(entity);
	}

	private Integer getPaymentChunkSize() {
		return Integer.parseInt(System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE"));
	}

}

