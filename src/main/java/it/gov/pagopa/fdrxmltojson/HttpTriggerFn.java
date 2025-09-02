package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;

/** Azure Functions with Azure Http trigger. */
@Slf4j
public class HttpTriggerFn {

  @FunctionName("HttpTriggerFn")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "HttpTriggerFn",
              methods = {HttpMethod.GET},
              route = "recover/fdr/{filename}",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("filename") String filename,
      final ExecutionContext context) {

    String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    String operation = String.format("HttpTriggerFn triggered at %s", triggeredAt);

    try {
      MDC.put("invocationId", context.getInvocationId());
      log.info(operation);

      if (filename.isEmpty()) {
        throw new InvalidParameterException("Filename not found");
      }

      processEntity(filename);

      log.info("{} executed", operation);
      return request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();

    } catch (Exception e) {
      log.error("{} error", operation, e);

      return request
          .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(HttpStatus.INTERNAL_SERVER_ERROR + ": " + ExceptionUtils.getStackTrace(e))
          .build();
    } finally {
      MDC.clear();
    }
  }

  private void processEntity(String filename) throws Exception {
    BlobData blobData = StorageAccountUtil.getBlobContent(filename);
    byte[] content = blobData.getContent();
    String sessionId = blobData.getMetadata().get(AppConstant.columnFieldSessionId);

    MDC.put("fileName", filename);
    MDC.put("sessionId", sessionId);
    // retryAttempt to 0 because it is an external call
    new FdrXmlCommon().convertXmlToJson(content, 0, true);
  }
}
