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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;

import java.security.InvalidParameterException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Azure Functions with Azure Http trigger.
 */
@Slf4j
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

		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		String operation  = String.format("XmlErrorRetry triggered at %s", triggeredAt);

		try{
			MDC.put("invocationId", context.getInvocationId());
			log.info(operation);
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");

			HttpResponseMessage responseMessage;
			if(partitionKey != null && rowKey != null) {
				ErrorRecoveryResponse response = processEntity(partitionKey, rowKey);

				HttpStatus status = "KO".equals(response.getStatus()) ?
						HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
                responseMessage = request
                        .createResponseBuilder(status)
                        .body(response.getDescription())
                        .build();

			} else {
				Map<String, ErrorRecoveryResponse> response = processAllEntities();
				HttpStatus status = response.values().stream()
						.anyMatch(i -> "KO".equals(i.getStatus())) ?
						HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;

				responseMessage = request
						.createResponseBuilder(status)
						.body(response)
						.build();
			}
			log.info("{} executed", operation);
			return responseMessage;

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


	@FunctionName("ErrorRecoveryFn")
	public HttpResponseMessage runErrorRecovery (
			@HttpTrigger(name = "ErrorRecoveryFn",
					methods = {HttpMethod.POST},
					route = "recover/errors",
					authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		String triggeredAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		String operation = String.format("ErrorRecoveryFn triggered at %s", triggeredAt);

		try {
			MDC.put("invocationId", context.getInvocationId());
			log.info(operation);

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
				Map<String, ErrorRecoveryResponse> response = processAllEntitiesByPartitionKey(errorRecoveryRequest.getPartitionKey());
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
						errorRecoveryRequest.getPartitionKey(), errorRecoveryRequest.getRowKeys());
				HttpStatus status = response.values().stream()
						.anyMatch(i -> "KO".equals(i.getStatus())) ?
						HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
				return request
						.createResponseBuilder(status)
						.body(response)
						.build();
			}
		} finally {
			MDC.clear();
		}
	}

	private ErrorRecoveryResponse processEntity(String partitionKey, String rowKey) {

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

		MDC.put("sessionId", sessionId);
		MDC.put("fileName", fileName);
		try {
			// retryAttempt to 0 because it is an external call
			fdrXmlCommon.convertXmlToJson(content, 0, true);
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

	private Map<String, ErrorRecoveryResponse> processAllEntities() {
		TableClient tableClient = StorageAccountUtil.getTableClient();
		Iterator<TableEntity> itr = tableClient.listEntities().iterator();
		return processBatchEntities(itr);
	}

	private Map<String, ErrorRecoveryResponse> processAllEntitiesByPartitionKey(String partitionKey) {
		TableClient tableClient = StorageAccountUtil.getTableClient();

		ListEntitiesOptions listEntitiesOptions = new ListEntitiesOptions()
				.setFilter(String.format("PartitionKey eq '%s'", partitionKey));

		Iterator<TableEntity> itr = tableClient.listEntities(listEntitiesOptions, Duration.ofSeconds(30), null).iterator();

		return processBatchEntities(itr);
	}

	private Map<String, ErrorRecoveryResponse> processAllEntitiesByPartitionKeyAndRowKeys(String partitionKey, List<String> rowKeys) {
		TableClient tableClient = StorageAccountUtil.getTableClient();

		String rowKeyString = rowKeys.stream()
				.map(rk -> String.format("RowKey eq '%s'", rk))
				.collect(Collectors.joining(" or "));

		ListEntitiesOptions listEntitiesOptions = new ListEntitiesOptions()
				.setFilter(String.format("PartitionKey eq '%s' and (%s)", partitionKey, rowKeyString));

		Iterator<TableEntity> itr = tableClient.listEntities(listEntitiesOptions, Duration.ofSeconds(30), null).iterator();

		return processBatchEntities(itr);
	}

	private Map<String, ErrorRecoveryResponse> processBatchEntities(Iterator<TableEntity> iterator) {
		Map<String, ErrorRecoveryResponse> responses = new HashMap<>();
		while (iterator.hasNext()) {
			TableEntity row = iterator.next();
			ErrorRecoveryResponse response = processEntity(row.getPartitionKey(), row.getRowKey());
			responses.put(row.getRowKey(), response);
		}
		return responses;
	}
}
