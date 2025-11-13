package it.gov.pagopa.fdrxmltojson;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;

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
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.xml.stream.XMLStreamException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.AddPaymentRequest;
import org.openapitools.client.model.CreateRequest;
import org.openapitools.client.model.ErrorResponse;
import org.openapitools.client.model.Payment;
import org.slf4j.MDC;

@Slf4j
public class FdrXmlCommon {

  private static final String D_D = "%d-%d";

  public void convertXmlToJson(byte[] content, long retryAttempt, boolean tryToDelete)
      throws IOException, XMLStreamException, JAXBException {

    log.info("Performing blob processing [retry attempt {}]", retryAttempt);

    if (GZipUtil.isGzip(content)) {

      // decompress GZip file
      InputStream decompressedStream = GZipUtil.decompressGzip(content);

      XMLParser parser = new XMLParser();
      NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest =
          parser.getInstanceByStAX(decompressedStream, NodoInviaFlussoRendicontazioneRequest.class);
      CtFlussoRiversamento ctFlussoRiversamento =
          parser.getInstanceByBytes(
              nodoInviaFlussoRendicontazioneRequest.getXmlRendicontazione(),
              CtFlussoRiversamento.class);

      // extract pathParam for FdR
      String fdr = nodoInviaFlussoRendicontazioneRequest.getIdentificativoFlusso();
      String pspId = nodoInviaFlussoRendicontazioneRequest.getIdentificativoPSP();
      MDC.put("psp", pspId);

      // delete previous create FDR flow
      deleteFdrFlow(tryToDelete, fdr, pspId, retryAttempt);

      // call create FDR flow
      createFdRFlow(
          fdr, pspId, nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento, retryAttempt, tryToDelete);

      // call add FDR payments
      addFdRPayments(fdr, pspId, ctFlussoRiversamento, retryAttempt);

      // call publish FdR flow
      publishFdRFlow(fdr, pspId, retryAttempt);
    } else {
      String sessionId = MDC.get("sessionId");
      String invocationId = MDC.get("invocationId");
      String fileName = MDC.get("fileName");
      String errMsg =
          messageFormat(
              sessionId,
              invocationId,
              null,
              fileName,
              "File error [retry attempt %d]",
              retryAttempt);
      throw new IllegalArgumentException(errMsg);
    }
  }

  private enum HttpEventTypeEnum {
    INTERNAL_DELETE,
    INTERNAL_CREATE,
    INTERNAL_ADD_PAYMENT,
    INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
    INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
    INTERNAL_PUBLISH;
  }

  /*
  private void deleteFdrFlow(boolean tryToDelete, String fdr, String pspId) {
    if (tryToDelete) {
      try {
        FdR3ClientUtil.getPspApi().internalDelete(fdr, pspId); // clears the entire stream from FDR
      } catch (ApiException e) {
        log.warn("Delete previous fdr flow - failed {}", e.getResponseBody(), e);
      }
    }
  }*/
  
  // [PIDM-1766] When enabled, delete any previous flow before recreating it.
  // This is required to avoid mixing a partial REST upload with the SOAP-to-REST translation flow.
  private void deleteFdrFlow(boolean tryToDelete, String fdr, String pspId, long retryAttempt) {
      if (!tryToDelete) {
          log.debug("Preventive delete skipped [fdr={}, pspId={}]", fdr, pspId);
          return;
      }

      try {
          FdR3ClientUtil.getPspApi().internalDelete(fdr, pspId); // clears the entire stream from FDR
          log.info("Previous unpublished flow deleted [fdr={}, pspId={}]", fdr, pspId);
      } catch (ApiException e) {
          if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
              // Nothing to delete: this is the expected case when no previous flow exists.
              log.info("No previous unpublished flow found [fdr={}, pspId={}]", fdr, pspId);
              return;
          }        
          // Any delete error different from NOT_FOUND must stop the processing.
          // Continuing from a dirty state may generate duplicated payments.
          log.error(
                  "Preventive delete failed [fdr={}, pspId={}, statusCode={}] {}",
                  fdr,
                  pspId,
                  e.getCode(),
                  e.getResponseBody(),
                  e);

          saveOnTableAndThrow(
                  pspId,
                  fdr,
                  ErrorEnum.HTTP_ERROR,
                  HttpEventTypeEnum.INTERNAL_DELETE,
                  String.valueOf(e.getCode()),
                  String.valueOf(retryAttempt),
                  e);
      }
  }

  
  // [PIDM-1766] Create the flow only after the previous state has been cleaned up.
  // If the flow still exists after preventive delete, the process must fail.
  private void createFdRFlow(
          String fdr,
          String pspId,
          NodoInviaFlussoRendicontazioneRequest nodoInviaFlussoRendicontazioneRequest,
          CtFlussoRiversamento ctFlussoRiversamento,
          long retryAttempt,
          boolean tryToDelete)
                  throws IOException {

      String operation = "Create request";
      log.info(operation);

      try {
          FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();

          // create body for create FDR
          CreateRequest createRequest =
                  fdR3ClientUtil.getCreateRequest(
                          nodoInviaFlussoRendicontazioneRequest, ctFlussoRiversamento);

          FdR3ClientUtil.getPspApi().internalCreate(fdr, pspId, createRequest);

      } catch (ApiException e) {
          handleCreateFlowException(fdr, pspId, retryAttempt, tryToDelete, operation, e);
      }
  }
  
  private void handleCreateFlowException(
          String fdr,
          String pspId,
          long retryAttempt,
          boolean tryToDelete,
          String operation,
          ApiException e) {

      if (e.getCode() != HttpStatus.BAD_REQUEST.value()) {
          saveCreateError(fdr, pspId, retryAttempt, String.valueOf(e.getCode()), e);
          return;
      }

      ErrorResponse errorBody;
      try {
          errorBody = ErrorResponse.fromJson(e.getResponseBody());
      } catch (IOException ioException) {
          // If the error body cannot be parsed, fall back to generic create error handling.
          log.error(
                  "{} failed: unable to parse error response body [fdr={}, pspId={}]",
                  operation,
                  fdr,
                  pspId,
                  ioException);

          saveCreateError(fdr, pspId, retryAttempt, String.valueOf(e.getCode()), e);
          return;
      }

      if (errorBody == null) {
          saveCreateError(fdr, pspId, retryAttempt, String.valueOf(e.getCode()), e);
          return;
      }

      handleCreateBadRequest(fdr, pspId, retryAttempt, tryToDelete, operation, errorBody, e);
  }
  
  private void handleCreateBadRequest(
          String fdr,
          String pspId,
          long retryAttempt,
          boolean tryToDelete,
          String operation,
          ErrorResponse errorBody,
          ApiException e) {

      String appErrorCode = errorBody.getAppErrorCode();

      if (AppConstant.FDR_FLOW_ALREADY_CREATED.equals(appErrorCode)) {
          handleAlreadyCreatedFlow(fdr, pspId, retryAttempt, tryToDelete, operation, e);
          return;
      }

      log.error("{} error [appErrorCode: {}]", operation, appErrorCode);

      saveCreateError(
              fdr,
              pspId,
              retryAttempt,
              Optional.ofNullable(appErrorCode).orElse(String.valueOf(e.getCode())),
              e);
  }
  
  private void handleAlreadyCreatedFlow(
          String fdr,
          String pspId,
          long retryAttempt,
          boolean tryToDelete,
          String operation,
          ApiException e) {

      if (tryToDelete) {
          // After a preventive delete, FLOW_ALREADY_CREATED means the cleanup did not succeed.
          // Must stop to avoid appending payments to a stale flow.
          log.error(
                  "{} failed: flow still exists after preventive delete [fdr={}, pspId={}]",
                  operation,
                  fdr,
                  pspId);

          saveCreateError(
                  fdr,
                  pspId,
                  retryAttempt,
                  AppConstant.FDR_FLOW_ALREADY_CREATED,
                  e);
          return;
      }

      // Tolerate already existing flow only when preventive delete is not required.
      log.warn(
              "{} skipped: flow already exists [fdr={}, pspId={}]",
              operation,
              fdr,
              pspId);
  }
  
  private void saveCreateError(
          String fdr,
          String pspId,
          long retryAttempt,
          String errorCode,
          ApiException e) {

      saveOnTableAndThrow(
              pspId,
              fdr,
              ErrorEnum.HTTP_ERROR,
              HttpEventTypeEnum.INTERNAL_CREATE,
              errorCode,
              String.valueOf(retryAttempt),
              e);
  }

  private void addFdRPayments(
      String fdr, String pspId, CtFlussoRiversamento ctFlussoRiversamento, long retryAttempt)
      throws IOException {
    List<CtDatiSingoliPagamenti> datiSingoliPagamenti =
        ctFlussoRiversamento.getDatiSingoliPagamenti();
    int chunkSize = getPaymentChunkSize();
    FdR3ClientUtil fdR3ClientUtil = new FdR3ClientUtil();
    List<AddPaymentRequest> addPaymentRequestList =
        fdR3ClientUtil.getAddPaymentRequestListChunked(datiSingoliPagamenti, chunkSize);
    // call addPayment FDR for each partition
    for (AddPaymentRequest addPaymentRequest : addPaymentRequestList) {
      sendAddFdrPayments(fdr, pspId, addPaymentRequest, retryAttempt, 0);
    }
  }

  private void sendAddFdrPayments(
      String fdr,
      String pspId,
      AddPaymentRequest addPaymentRequest,
      long retryAttempt,
      int internalRetry)
      throws IOException {
    String operation =
        String.format(
            "Add payments request [retryAttempt: %d][internalRetry: %d]",
            retryAttempt, internalRetry);

    try {
      int chunkSize = addPaymentRequest.getPayments().size();
      log.info("{} [chunk size: {}]", operation, chunkSize);
      FdR3ClientUtil.getPspApi().internalAddPayment(fdr, pspId, addPaymentRequest);
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.BAD_REQUEST.value()) {
        ErrorResponse errorBody = ErrorResponse.fromJson(e.getResponseBody());
        if (errorBody != null) {
          String appErrorCode = errorBody.getAppErrorCode();

          if (appErrorCode == null || !appErrorCode.equals(AppConstant.FDR_PAYMENT_ALREADY_ADDED)) {
            saveOnTableAndThrow(
                pspId,
                fdr,
                ErrorEnum.HTTP_ERROR,
                HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
                Optional.ofNullable(appErrorCode).orElse("NA"),
                String.format(D_D, retryAttempt, internalRetry),
                e);
          } else {
            // case FDR_PAYMENT_ALREADY_ADDED
            if (internalRetry < 2) {
              ErrorResponse errorResponse = ErrorResponse.fromJson(e.getResponseBody());
              if (errorResponse.getErrors() != null && !errorResponse.getErrors().isEmpty()) {
                String path = errorResponse.getErrors().get(0).getPath();
                if (path != null) {
                  List<Long> indexes =
                      Arrays.stream(path.replaceAll("[\\[\\] ]", "").split(","))
                          .map(Long::parseLong)
                          .toList();
                  List<Payment> paymentsFiltered =
                      addPaymentRequest.getPayments().stream()
                          .filter(payment -> !indexes.contains(payment.getIndex()))
                          .toList();
                  if (!paymentsFiltered.isEmpty()) {
                    addPaymentRequest.setPayments(paymentsFiltered);

                    log.debug("{} try to send chunk without conflicting indexes", operation);
                    sendAddFdrPayments(
                        fdr, pspId, addPaymentRequest, retryAttempt, internalRetry + 1);
                  } else {
                    log.debug("{} already added. Nothing to internalRetry", operation);
                  }
                } else {
                  saveOnTableAndThrow(
                      pspId,
                      fdr,
                      ErrorEnum.HTTP_ERROR,
                      HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
                      appErrorCode,
                      String.format("%d-%d. Path not found", retryAttempt, internalRetry),
                      e);
                }
              } else {
                saveOnTableAndThrow(
                    pspId,
                    fdr,
                    ErrorEnum.HTTP_ERROR,
                    HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_ERROR_RESPONSE_EMPTY,
                    appErrorCode,
                    String.format(D_D, retryAttempt, internalRetry),
                    e);
              }
            } else {
              saveOnTableAndThrow(
                  pspId,
                  fdr,
                  ErrorEnum.HTTP_ERROR,
                  HttpEventTypeEnum.INTERNAL_ADD_PAYMENT_RETRY_EXCEEDED,
                  appErrorCode,
                  String.format(D_D, retryAttempt, internalRetry),
                  e);
            }
          }
        } else {
          saveOnTableAndThrow(
              pspId,
              fdr,
              ErrorEnum.HTTP_ERROR,
              HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
              String.valueOf(e.getCode()),
              String.format(D_D, retryAttempt, internalRetry),
              e);
        }
      } else {
        saveOnTableAndThrow(
            pspId,
            fdr,
            ErrorEnum.HTTP_ERROR,
            HttpEventTypeEnum.INTERNAL_ADD_PAYMENT,
            String.valueOf(e.getCode()),
            String.format(D_D, retryAttempt, internalRetry),
            e);
      }
    }
  }

  private void publishFdRFlow(String fdr, String pspId, long retryAttempt) {
    log.info("Publish request");
    try {
      FdR3ClientUtil.getPspApi().internalPublish(fdr, pspId);
    } catch (ApiException e) {
      if (e.getCode() != HttpStatus.NOT_FOUND.value()) {
        saveOnTableAndThrow(
            pspId,
            fdr,
            ErrorEnum.HTTP_ERROR,
            HttpEventTypeEnum.INTERNAL_PUBLISH,
            String.valueOf(e.getCode()),
            String.valueOf(retryAttempt),
            e);
      }
    }
  }

  private void saveOnTableAndThrow(
      String pspId,
      String fdr,
      ErrorEnum error,
      HttpEventTypeEnum httpEventTypeEnum,
      String errorCode,
      String retryAttempt,
      ApiException e) {
    Instant now = Instant.now();
    String sessionId = MDC.get("sessionId");
    String invocationId = MDC.get("invocationId");
    String fileName = MDC.get("fileName");

    Map<String, Object> errorMap = new LinkedHashMap<>();
    errorMap.put(AppConstant.columnFieldSessionId, sessionId);
    errorMap.put(AppConstant.columnFieldCreated, now);
    errorMap.put(AppConstant.columnFieldFileName, fileName);
    errorMap.put(AppConstant.columnFieldFdr, fdr);
    errorMap.put(AppConstant.columnFieldPspId, pspId);
    errorMap.put(AppConstant.columnFieldErrorType, error.name());
    errorMap.put(AppConstant.columnFieldHttpEventType, httpEventTypeEnum.name());
    Optional.ofNullable(e.getResponseBody())
        .ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResponse, a));
    Optional.ofNullable(errorCode)
        .ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorCode, a));
    errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));
    errorMap.put(AppConstant.columnFieldRetryAttempt, retryAttempt);

    String partitionKey = LocalDate.ofInstant(now, TimeZone.getDefault().toZoneId()).toString();

    TableClient tableClient = StorageAccountUtil.getTableClient();
    TableEntity entity = new TableEntity(partitionKey, sessionId);
    entity.setProperties(errorMap);
    tableClient.upsertEntity(entity);

    String message =
        messageFormat(
            sessionId,
            invocationId,
            pspId,
            fileName,
            "[FdrXmlToJson][%s][httpEventTypeEnum=%s][errorCode=%s] Http error at %s",
            error.name(),
            httpEventTypeEnum.name(),
            errorCode,
            now);
    throw new AppException(message, e);
  }

  private Integer getPaymentChunkSize() {
    return Integer.parseInt(System.getenv("ADD_PAYMENT_REQUEST_PARTITION_SIZE"));
  }
}
