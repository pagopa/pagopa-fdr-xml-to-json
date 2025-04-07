package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;


/**
 * Azure Functions with Azure Http trigger.
 */
public class HttpTriggerFn {

	private String operation = "HttpTriggerFn ";

	@FunctionName("HttpTriggerFn")
	public HttpResponseMessage run (
			@HttpTrigger(name = "HttpTriggerFn",
			methods = {HttpMethod.GET},
			route = "fdr",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		operation = String.format("%s triggered at %s",  operation, triggeredAt);

		logger.log(Level.INFO, () -> messageFormat("NA", context.getInvocationId(), "NA", "NA", operation));

		try {
			String filename = request.getQueryParameters().get("filename");

			if (filename.isEmpty()) {
				throw new InvalidParameterException("Filename not found");
			}

			HttpResponseMessage response;

			response = processEntity(context, filename);

			if (response == null) {
				logger.log(Level.INFO, () -> messageFormat("NA", context.getInvocationId(), "NA", "NA", "%s executed", operation));
				response = request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
			}

			return response;

		} catch (Exception e) {
			logger.log(Level.SEVERE, () -> messageFormat("NA", context.getInvocationId(), "NA", "NA", "%s error: %s", operation, e.getMessage()));

			return request
					.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(HttpStatus.INTERNAL_SERVER_ERROR + ": " + ExceptionUtils.getStackTrace(e))
					.build();
		}
	}

	private HttpResponseMessage processEntity(ExecutionContext context, String filename) throws Exception {

		BlobData blobData = StorageAccountUtil.getBlobContent(filename);
		byte[] content = blobData.getContent();
		String sessionId = blobData.getMetadata().get(AppConstant.columnFieldSessionId);

		// retryAttempt to 0 because it is an external call
		new FdrXmlCommon().convertXmlToJson(context, sessionId, content, filename, 0, true);

		return null;
	}

}
