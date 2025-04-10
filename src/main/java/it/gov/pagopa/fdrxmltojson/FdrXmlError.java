package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.model.ErrorRecoveryRequest;
import it.gov.pagopa.fdrxmltojson.model.ErrorRecoveryResponse;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.security.InvalidParameterException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;


/**
 * Azure Functions with Azure Http trigger.
 */
public class FdrXmlError {

	private FdrXmlCommon fdrXmlCommon;

	public FdrXmlError() {
		this.fdrXmlCommon = new FdrXmlCommon();
	}

	public FdrXmlError(FdrXmlCommon fdrXmlCommon) {
		this.fdrXmlCommon = fdrXmlCommon;
	}

	// recover all or specific row
	@FunctionName("XmlErrorRetry")
	public HttpResponseMessage run (
			@HttpTrigger(name = "XmlErrorRetryTrigger",
			methods = {HttpMethod.GET},
			route = "recover/errors",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		String operation = "XmlErrorRetry ";
		operation = String.format("%s triggered at %s",  operation, triggeredAt);

		log(logger, Level.INFO, messageFormat("NA", context.getInvocationId(), "NA", "NA", operation));

		try{
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");

			if(partitionKey != null && rowKey != null) {
				ErrorRecoveryResponse response = processEntity(context, partitionKey, rowKey);

				log(logger, Level.INFO, messageFormat("NA", context.getInvocationId(), "NA", "NA", "%s executed", operation));

				HttpStatus status = "KO".equals(response.getStatus()) ?
						HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
				return request
						.createResponseBuilder(status)
						.body(response.getDescription())
						.build();

			} else {
				Map<String, ErrorRecoveryResponse> response = processAllEntities(context, request);
				HttpStatus status = response.values().stream()
						.anyMatch(i -> "KO".equals(i.getStatus())) ?
						HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;

				log(logger, Level.INFO, messageFormat("NA", context.getInvocationId(), "NA", "NA", "%s executed", operation));

				return request
						.createResponseBuilder(status)
						.body(response)
						.build();
			}

		} catch (Exception e) {
			log(logger, Level.SEVERE, messageFormat("NA", context.getInvocationId(), "NA", "NA", "%s error: %s", operation, e.getMessage()));

			return request
					.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(HttpStatus.INTERNAL_SERVER_ERROR + ": " + ExceptionUtils.getStackTrace(e))
					.build();
		}
	}


	@FunctionName("ErrorRecoveryFn")
	public HttpResponseMessage runErrorRecovery (
			@HttpTrigger(name = "ErrorRecoveryFn",
					methods = {HttpMethod.POST},
					route = "recover/errors",
					authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		String operation = "ErrorRecoveryFn ";
		operation = String.format("%s triggered at %s",  operation, triggeredAt);

		log(logger, Level.INFO, messageFormat("NA", context.getInvocationId(), "NA", "NA", operation));

		String body = request.getBody().orElseThrow(() -> new InvalidParameterException("Body not found"));
		ObjectMapper mapper = new ObjectMapper();
		ErrorRecoveryRequest errorRecoveryRequest;
		try {
			errorRecoveryRequest = mapper.readValue(body, ErrorRecoveryRequest.class);
		} catch (JsonProcessingException e) {
			throw new InvalidParameterException("Json processing error");
		}

		if (errorRecoveryRequest.getPartitionKey().isEmpty()) {
			return request
					.createResponseBuilder(HttpStatus.BAD_REQUEST)
					.body("PartitionKey not found")
					.build();
		}

		if (errorRecoveryRequest.getRowKeys() == null || errorRecoveryRequest.getRowKeys().isEmpty()) {
			Map<String, ErrorRecoveryResponse> response = processAllEntitiesByPartitionKey(context, request, errorRecoveryRequest.getPartitionKey());
			HttpStatus status = response.values().stream()
					.anyMatch(i -> "KO".equals(i.getStatus())) ?
					HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
			return request
					.createResponseBuilder(status)
					.body(response)
					.build();
		}
		else {
			Map<String, ErrorRecoveryResponse> response = processAllEntitiesByPartitionKeyAndRowKeys(
					context, request, errorRecoveryRequest.getPartitionKey(), errorRecoveryRequest.getRowKeys());
			HttpStatus status = response.values().stream()
					.anyMatch(i -> "KO".equals(i.getStatus())) ?
					HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
			return request
					.createResponseBuilder(status)
					.body(response)
					.build();
		}
	}

	private void log(Logger logger, Level level, String message) {
		logger.log(level, () -> message);
	}

	private ErrorRecoveryResponse processEntity(ExecutionContext context,
												String partitionKey, String rowKey) {

		TableClient tableClient = StorageAccountUtil.getTableClient();

		TableEntity tableEntity;
		try {
			tableEntity = tableClient.getEntity(partitionKey, rowKey);
		} catch (TableServiceException e) {
			return ErrorRecoveryResponse.builder()
					.status("KO")
					.description(String.format("Table entity with partitionKey=%s and rowKey=%s not found", partitionKey, rowKey))
					.build();
		}

		String fileName  = (String) tableEntity.getProperty(AppConstant.columnFieldFileName);
		String fdr       = (String) tableEntity.getProperty(AppConstant.columnFieldFdr);
		String pspId     = (String) tableEntity.getProperty(AppConstant.columnFieldPspId);

		BlobData blobData = StorageAccountUtil.getBlobContent(fileName);
		byte[] content = blobData.getContent();
		String sessionId = blobData.getMetadata().get(AppConstant.columnFieldSessionId);

		try {
			// retryAttempt to 0 because it is an external call
			fdrXmlCommon.convertXmlToJson(context, sessionId, content, fileName, 0, true);
			tableClient.deleteEntity(tableEntity);
		} catch (Exception e) {
			return ErrorRecoveryResponse.builder()
					.status("KO")
					.description(String.format("Failed to convert fdr=%s and pspId=%s: %s", fdr, pspId, e.getMessage()))
					.build();
		}

		return ErrorRecoveryResponse.builder()
				.status("OK")
				.description("")
				.build();
	}

	private Map<String, ErrorRecoveryResponse> processAllEntities(ExecutionContext context, HttpRequestMessage<Optional<String>> request) {
		TableClient tableClient = StorageAccountUtil.getTableClient();
		Iterator<TableEntity> itr = tableClient.listEntities().iterator();
		return processBatchEntities(context, request, itr);
	}

	private Map<String, ErrorRecoveryResponse> processAllEntitiesByPartitionKey(ExecutionContext context, HttpRequestMessage<Optional<String>> request, String partitionKey) {
		TableClient tableClient = StorageAccountUtil.getTableClient();

		ListEntitiesOptions listEntitiesOptions = new ListEntitiesOptions()
				.setFilter(String.format("PartitionKey eq '%s'", partitionKey));

		Iterator<TableEntity> itr = tableClient.listEntities(listEntitiesOptions, Duration.ofSeconds(30), null).iterator();

		return processBatchEntities(context, request, itr);
	}

	private Map<String, ErrorRecoveryResponse> processAllEntitiesByPartitionKeyAndRowKeys(ExecutionContext context, HttpRequestMessage<Optional<String>> request, String partitionKey, List<String> rowKeys) {
		TableClient tableClient = StorageAccountUtil.getTableClient();

		String rowKeyString = rowKeys.stream()
				.map(rk -> String.format("RowKey eq '%s'", rk))
				.collect(Collectors.joining(" or "));

		ListEntitiesOptions listEntitiesOptions = new ListEntitiesOptions()
				.setFilter(String.format("PartitionKey eq '%s' and (%s)", partitionKey, rowKeyString));

		Iterator<TableEntity> itr = tableClient.listEntities(listEntitiesOptions, Duration.ofSeconds(30), null).iterator();

		return processBatchEntities(context, request, itr);
	}

	private Map<String, ErrorRecoveryResponse> processBatchEntities(ExecutionContext context, HttpRequestMessage<Optional<String>> request, Iterator<TableEntity> iterator) {
		Map<String, ErrorRecoveryResponse> responses = new HashMap<>();
		while (iterator.hasNext()) {
			TableEntity row = iterator.next();
			ErrorRecoveryResponse response = processEntity(context, row.getPartitionKey(), row.getRowKey());
			responses.put(row.getRowKey(), response);
		}
		return responses;
	}
}
