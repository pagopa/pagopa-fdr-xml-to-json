package it.gov.pagopa.fdrxmltojson;


import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoliPagamenti;
import it.gov.digitpa.schemas._2011.pagamenti.CtFlussoRiversamento;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.ErrorEnum;
import it.gov.pagopa.fdrxmltojson.util.*;
import it.gov.pagopa.pagopa_api.node.nodeforpsp.NodoInviaFlussoRendicontazioneRequest;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.AddPaymentRequest;
import org.openapitools.client.model.CreateRequest;
import org.openapitools.client.model.ErrorResponse;
import org.openapitools.client.model.Payment;
import org.slf4j.MDC;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;

@Slf4j
public class FdrXmlCommon {


	public void convertXmlToJson(
								 byte[] content,
								 long retryAttempt,
								 boolean tryToDelete) throws IOException, XMLStreamException, JAXBException {


		log.info("Performing blob processing [retry attempt {}]", retryAttempt);

		if (GZipUtil.isGzip(content)) {

			// decompress GZip file
			InputStream decompressedStream = GZipUtil.decompressGzip(content);

			XMLParser parser = new XMLParser();
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest = parser.getInstanceByStAX(decompressedStream, NodoInviaFlussoRendicontazioneRequest.class);
			CtFlussoRiversamento ctFlussoRiversamento = parser.getInstanceByBytes(nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(), CtFlussoRiversamento.class);

			// extract pathParam for FdR
			String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
			String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();
			MDC.put("psp", pspId);

			// delete previous create FDR flow
			deleteFdrFlow(tryToDelete, fdr, pspId);

			// call create FDR flow
			createFdRFlow(fdr, pspId, nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento, retryAttempt);

			// call add FDR payments
			addFdRPayments(fdr, pspId, ctFlussoRiversamento, retryAttempt);

			// call publish FdR flow
			publishFdRFlow(fdr, pspId, retryAttempt);
		}
		else {
			String sessionId = MDC.get("sessionId");
			String invocationId = MDC.get("invocationId");
			String fileName = MDC.get("fileName");
			String errMsg = messageFormat(sessionId, invocationId, null, fileName, "File error [retry attempt %d]", retryAttempt);
			throw new IllegalArgumentException(errMsg);
		}
	}

	private enum HttpEventTypeEnum {
		INTERNAL_CREATE,
		INTERNAL_ADD_PAYMENT,
		INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
		INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
		INTERNAL_PUBLISH;
	}

	private void deleteFdrFlow(boolean tryToDelete, String fdr, String pspId) {
		if (tryToDelete) {
			try {
				FdR3ClientUtil.getPspApi().internalDelete(fdr, pspId);  // clears the entire stream from FDR
			} catch (ApiException e) {
				log.warn("Delete previous fdr flow - failed {}", e.getResponseBody(), e);
			}
		}
	}

	private void createFdRFlow(
			String fdr, String pspId,
			NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest,
			CtFlussoRiversamento ctFlussoRiversamento, long retryAttempt) throws IOException {

		String operation = "Create request";
		log.info(operation);
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
						log.error("{} error [appErrorCode: {}]", operation, appErrorCode);

						generateAlertAndSaveOnTable(
								pspId, fdr,
								ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
								appErrorCode, String.valueOf(retryAttempt), e
						);
					}
				}
				else {
					generateAlertAndSaveOnTable(
							pspId, fdr,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
							String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
					);
				}
			}
			else {
				generateAlertAndSaveOnTable(
						pspId, fdr,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_CREATE,
						String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
				);
			}
		}
	}


	private void addFdRPayments(String fdr, String pspId, CtFlussoRiversamento ctFlussoRiversamento, long retryAttempt) throws IOException {
		List<CtDatiSingoliPagamenti> datiSingoliPagamenti = ctFlussoRiversamento.getDatiSingoliPagamenti();
		int chunkSize = getPaymentChunkSize();
		FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
		List<AddPaymentRequest> addPaymentRequestList = fdR3ClientUtil.getAddPaymentRequestListChunked(datiSingoliPagamenti, chunkSize);
		// call addPayment FDR for each partition
		for (AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
			sendAddFdrPayments(fdr, pspId, addPaymentRequest, retryAttempt, 0);
		}
	}

	private void sendAddFdrPayments(String fdr, String pspId, AddPaymentRequest addPaymentRequest, long retryAttempt, int internalRetry) throws IOException {
		String operation = String.format("Add payments request [retryAttempt: %d][internalRetry: %d]", retryAttempt, internalRetry);

		try {
			int chunkSize = addPaymentRequest.getPayments().size();
			log.info("{} [chunk size: {}]", operation, chunkSize);
			FdR3ClientUtil.getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
		}
		catch (ApiException e) {
			if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
				ErrorResponse errorBody = ErrorResponse.fromJson(e.getResponseBody());
				if (errorBody != null) {
					String appErrorCode = errorBody.getAppErrorCode();

					if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_PAYMENT_ALREADY_ADDED)) {
						generateAlertAndSaveOnTable(
								pspId, fdr,
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

										log.debug("{} try to send chunk without conflicting indexes", operation);
										sendAddFdrPayments(fdr, pspId, addPaymentRequest, retryAttempt, internalRetry + 1);
									} else {
										log.debug("{} already added. Nothing to internalRetry", operation);
									}
								} else {
									generateAlertAndSaveOnTable(
											pspId, fdr,
											ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
											appErrorCode, String.format("%d-%d. Path not found", retryAttempt, internalRetry), e
									);
								}
							} else {
								generateAlertAndSaveOnTable(
										pspId, fdr,
										ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
										appErrorCode, String.format("%d-%d", retryAttempt, internalRetry), e
								);
							}
						} else {
							generateAlertAndSaveOnTable(
									pspId, fdr,
									ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
									appErrorCode, String.format("%d-%d", retryAttempt, internalRetry), e
							);
						}
					}
				}
				else {
					generateAlertAndSaveOnTable(
							pspId, fdr,
							ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
							String.valueOf(e.getCode()), String.format("%d-%d", retryAttempt, internalRetry), e
					);
				}
			}
			else {
				generateAlertAndSaveOnTable(
						pspId, fdr,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
						String.valueOf(e.getCode()), String.format("%d-%d", retryAttempt, internalRetry), e
				);
			}
		}
	}

	private void publishFdRFlow(String fdr, String pspId, long retryAttempt) {
		log.info("Publish request");
		try {
			FdR3ClientUtil.getPspApi().internalPublish(fdr, pspId);
		} catch (ApiException e) {
			if (e.getCode() != HttpStatus.NOT_FOUND.value()) {
				generateAlertAndSaveOnTable(
						pspId, fdr,
						ErrorEnum.HTTP_ERROR, HttpEventTypeEnum.INTERNAL_PUBLISH,
						String.valueOf(e.getCode()), String.valueOf(retryAttempt), e
				);
			}
		}
	}

	private void generateAlertAndSaveOnTable(String pspId, String fdr, ErrorEnum error, HttpEventTypeEnum httpEventTypeEnum, String errorCode, String retryAttempt, ApiException e) {
		Instant now = Instant.now();
		String sessionId = MDC.get("sessionId");
		String invocationId = MDC.get("invocationId");
		String fileName = MDC.get("fileName");

		Map<String, Object> errorMap = buildTableMap(pspId, fdr, error, httpEventTypeEnum, errorCode, retryAttempt, e, sessionId, now, fileName);

		String partitionKey = LocalDate.ofInstant(now, TimeZone.getDefault().toZoneId()).toString();

		TableClient tableClient = StorageAccountUtil.getTableClient();
		TableEntity entity = new TableEntity(partitionKey, sessionId);
		entity.setProperties(errorMap);
		tableClient.upsertEntity(entity);

		String message = messageFormat(sessionId, invocationId, pspId, fileName, "[FdrXmlToJson][%s][httpEventTypeEnum=%s][errorCode=%s] Http error at %s", error.name(), httpEventTypeEnum.name(), errorCode, now);
		throw new AppException(message, e);
	}

	private static @NotNull Map<String, Object> buildTableMap(
			String pspId,
			String fdr,
			ErrorEnum error,
			HttpEventTypeEnum httpEventTypeEnum,
			String errorCode,
			String retryAttempt,
			ApiException e,
			String sessionId,
			Instant now,
			String fileName
	) {
		Map<String,Object> errorMap = new LinkedHashMap<>();
		errorMap.put(AppConstant.columnFieldSessionId, sessionId);
		errorMap.put(AppConstant.columnFieldCreated, now);
		errorMap.put(AppConstant.columnFieldFileName, fileName);
		errorMap.put(AppConstant.columnFieldFdr, fdr);
		errorMap.put(AppConstant.columnFieldPspId, pspId);
		errorMap.put(AppConstant.columnFieldErrorType, error.name());
		errorMap.put(AppConstant.columnFieldHttpEventType, httpEventTypeEnum.name());
		Optional.ofNullable(e.getResponseBody()).ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResponse, a));
		Optional.ofNullable(errorCode).ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorCode, a));
		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));
		errorMap.put(AppConstant.columnFieldRetryAttempt, retryAttempt);
		return errorMap;
	}

	private Integer getPaymentChunkSize() {
		return Integer.parseInt(System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE"));
	}
}

