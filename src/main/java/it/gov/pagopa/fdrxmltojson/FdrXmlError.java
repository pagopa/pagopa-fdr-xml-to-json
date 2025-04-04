package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.FdR3ClientUtil;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.ErrorResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;


/**
 * Azure Functions with Azure Http trigger.
 */
public class FdrXmlError {

	private String operation = "XmlErrorRetry ";


	@FunctionName("XmlErrorRetry")
	public HttpResponseMessage run (
			@HttpTrigger(name = "XmlErrorRetryTrigger",
			methods = {HttpMethod.GET},
			route = "xmlerror",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		operation = String.format("%s triggered at %s",  operation, triggeredAt);

		logger.log(Level.INFO, () -> messageFormat("NA", context.getInvocationId(), "NA", "NA", operation));

		try{
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");

			HttpResponseMessage response;

			if(partitionKey != null && rowKey != null) {
				response = processEntity(context, request, partitionKey, rowKey);
			} else {
				response = processAllEntities(context, request);
			}

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

	private HttpResponseMessage processEntity(ExecutionContext context, HttpRequestMessage<Optional<String>> request,
			String partitionKey, String rowKey) throws Exception {

		Logger logger = context.getLogger();

		TableClient tableClient = StorageAccountUtil.getTableClient();

		TableEntity tableEntity;
		try {
			tableEntity = tableClient.getEntity(partitionKey, rowKey);
		} catch (TableServiceException e) {
			return request
					.createResponseBuilder(HttpStatus.NOT_FOUND)
					.body(HttpStatus.NOT_FOUND + ": " + String.format("Table entity with partitionKey=%s and rowKey=%s not found", partitionKey, rowKey))
					.build();
		}

		String fileName  = (String) tableEntity.getProperty(AppConstant.columnFieldFileName);
		String fdr       = (String) tableEntity.getProperty(AppConstant.columnFieldFdr);
		String pspId     = (String) tableEntity.getProperty(AppConstant.columnFieldPspId);

		BlobData blobData = StorageAccountUtil.getBlobContent(fileName);
		byte[] content = blobData.getContent();
		String sessionId = blobData.getMetadata().get(AppConstant.columnFieldSessionId);
		
		// retryAttempt to 0 because it is an external call
		new FdrXmlCommon().convertXmlToJson(context, sessionId, content, fileName, 0);

		try {
			FdR3ClientUtil.getPspApi().internalDelete(fdr, pspId);  // clears the entire stream from FDR
		} catch (ApiException e) {
			String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
			if (e.getCode() == HttpStatus.NOT_FOUND.value() && AppConstant.FDR_FLOW_NOT_FOUND.equalsIgnoreCase(appErrorCode)) {
				logger.log(Level.WARNING, () -> messageFormat(sessionId, context.getInvocationId(), pspId, fileName, "%s - InternalDelete failed %s", operation, e.getResponseBody()));
				// anomaly is logged, no action is taken because the entity might not exist, meaning the issue is already resolved.
			} else {
				HttpStatus status = HttpStatus.valueOf(e.getCode());
				return request
						.createResponseBuilder(status)
						.body(status.toString() + String.format("Failed internalDelete call with fdr=%s and pspId=%s: %s", fdr, pspId, e.getResponseBody()))
						.build();
			}
		}

		tableClient.deleteEntity(tableEntity);

		return null;
	}

	private HttpResponseMessage processAllEntities(ExecutionContext context, HttpRequestMessage<Optional<String>> request) throws Exception {
		TableClient tableClient = StorageAccountUtil.getTableClient();
		Iterator<TableEntity> itr = tableClient.listEntities().iterator();
		while (itr.hasNext()) {
			HttpResponseMessage response = processEntity(context, request, itr.next().getPartitionKey(), itr.next().getRowKey());
			if (response != null) {
				return response;
			}
		}
		return null;
	}
}
