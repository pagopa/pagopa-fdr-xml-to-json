package it.gov.pagopa.fdrxmltojson;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.ErrorResponse;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import it.gov.pagopa.fdrxmltojson.util.ErrorCodes;


/**
 * Azure Functions with Azure Http trigger.
 */
public class FdrXmlError {


	// Thread-Safe Singleton for Clients
	private static class Clients {
		private static final TableServiceClient TABLE_SERVICE_CLIENT = new TableServiceClientBuilder()
				.connectionString(System.getenv("STORAGE_ACCOUNT_CONN_STRING"))
				.buildClient();

		private static final InternalPspApi PSP_API;
		static {
			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(System.getenv("FDR_NEW_API_KEY"));
			PSP_API = new InternalPspApi(apiClient);
			PSP_API.setCustomBaseUrl(System.getenv("FDR_NEW_BASE_URL"));
		}

		private static final BlobContainerClient BLOB_CONTAINER_CLIENT =
				new BlobServiceClientBuilder()
				.connectionString(System.getenv("STORAGE_ACCOUNT_CONN_STRING"))
				.buildClient()
				.getBlobContainerClient(System.getenv("FDR1_FLOW_BLOB_CONTAINER_NAME"));

		public static TableServiceClient getTableServiceClient() {
			return TABLE_SERVICE_CLIENT;
		}

		public static InternalPspApi getPspApi() {
			return PSP_API;
		}

		public static BlobContainerClient getBlobContainerClient() {
			return BLOB_CONTAINER_CLIENT;
		}
	}


	@FunctionName("XmlErrorRetry")
	public HttpResponseMessage run (
			@HttpTrigger(name = "XmlErrorRetryTrigger",
			methods = {HttpMethod.GET},
			route = "xmlerror",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();

		logger.log(Level.INFO, () -> String.format("[FDRXMLERROR] [invocationId: %s] Triggered at: %s", context.getInvocationId(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

		try{
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");

			HttpResponseMessage response;

			if(partitionKey != null && rowKey != null){
				response = processEntity(context, request, partitionKey, rowKey, System.getenv("ERROR_TABLE_NAME"));
			} else {
				response = processAllEntities(context, request, System.getenv("ERROR_TABLE_NAME"));
			}

			if (response == null) {
				logger.log(Level.INFO, () -> String.format("[FDRXMLERROR] [invocationId: %s] Done processing events at: %s", context.getInvocationId(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
				response = request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
			}

			return response;

		} catch (Exception e) {
			logger.log(Level.SEVERE, () -> String.format("[%s] [invocationId: %s] Generic error at %s: %s",ErrorCodes.FDR1XMLERROR_E1, context.getInvocationId(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),e.getMessage()));
			return request
					.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(HttpStatus.INTERNAL_SERVER_ERROR.toString() + ": " + ExceptionUtils.getStackTrace(e))
					.build();
		}
	}

	private HttpResponseMessage processEntity(ExecutionContext context, HttpRequestMessage<Optional<String>> request,
			String partitionKey, String rowKey, String tableName) throws IOException {

		Logger logger = context.getLogger();

		TableClient tableClient = Clients.getTableServiceClient().getTableClient(tableName);
		TableEntity tableEntity = null;
		try {
			tableEntity = tableClient.getEntity(partitionKey, rowKey);
		} catch (TableServiceException e) {
			return request
					.createResponseBuilder(HttpStatus.NOT_FOUND)
					.body(HttpStatus.NOT_FOUND.toString() + ": " + String.format("Not found Table entity with partitionKey=%s and rowKey=%s", partitionKey, rowKey))
					.build();
		}

		String fdr       = (String) tableEntity.getProperty(AppConstant.columnFieldFdr);
		String pspId     = (String) tableEntity.getProperty(AppConstant.columnFieldPspId);
		String fileName  = (String) tableEntity.getProperty(AppConstant.columnFieldFileName);
		String sessionId = (String) tableEntity.getProperty(AppConstant.columnFieldSessionId);


		byte[] content = Clients.getBlobContainerClient().getBlobClient(fileName).downloadContent().toBytes();
		new FdrXmlCommon().convertXmlToJson(context, sessionId, content, fileName);

		try {
			Clients.getPspApi().internalDelete(fdr, pspId);  // clears the entire stream from FDR
		} catch (ApiException e) {
			String appErrorCode = ErrorResponse.fromJson(e.getResponseBody()).getAppErrorCode();
			if (e.getCode() == HttpStatus.NOT_FOUND.value() && AppConstant.FDR_FLOW_NOT_FOUND.equalsIgnoreCase(appErrorCode)) {
				logger.log(Level.WARNING, 
						() -> String.format("[%s] [invocationId: %s] Failed internalDelete call at %s with fdr=%s and pspId=%s: %s ", 
								ErrorCodes.FDR1XMLERROR_E2, context.getInvocationId(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), fdr, pspId, e.getResponseBody()));
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

	private HttpResponseMessage processAllEntities(ExecutionContext context, HttpRequestMessage<Optional<String>> request, String tableName) throws IOException {
		TableClient tableClient = Clients.getTableServiceClient().getTableClient(tableName);
		Iterator<TableEntity> itr = tableClient.listEntities().iterator();
		while (itr.hasNext()) {
			HttpResponseMessage response = processEntity(context, request, itr.next().getPartitionKey(), itr.next().getRowKey(), tableName);
			if (response != null) {
				return response;
			}
		}
		return null;
	}
}
