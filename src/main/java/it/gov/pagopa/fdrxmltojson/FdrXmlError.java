package it.gov.pagopa.fdrxmltojson;

import com.azure.core.util.BinaryData;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.model.ErrorEnum;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;

import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Azure Functions with Azure Http trigger.
 */
public class FdrXmlError {

	private static final String fdrNewBaseUrl = System.getenv("FDR_NEW_BASE_URL");
	private static final String fdrNewApiKey = System.getenv("FDR_NEW_API_KEY");
	private static final String tableStorageConnString = System.getenv("TABLE_STORAGE_CONN_STRING");
	private static final String tableName = System.getenv("TABLE_STORAGE_TABLE_NAME");

	private static final String storageConnString = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
	private static final String blobContainerName = System.getenv("BLOB_CONTAINER_NAME");
	private static TableServiceClient tableServiceClient = null;
	private static InternalPspApi pspApi = null;
	private static BlobContainerClient blobContainerClient;

	private static TableServiceClient getTableServiceClient(){
		if(tableServiceClient==null){
			tableServiceClient = new TableServiceClientBuilder()
					.connectionString(tableStorageConnString)
					.buildClient();
			tableServiceClient.createTableIfNotExists(tableName);
		}
		return tableServiceClient;
	}

	private static InternalPspApi getPspApi(){
		if(pspApi==null){
			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(fdrNewApiKey);
			pspApi = new InternalPspApi(apiClient);
			pspApi.setCustomBaseUrl(fdrNewBaseUrl);
		}
		return pspApi;
	}

	private static BlobContainerClient getBlobContainerClient(){
		if(blobContainerClient==null){
			BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(storageConnString).buildClient();
			blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(blobContainerName);
		}
		return blobContainerClient;
	}
	@FunctionName("XmlErrorRetry")
	public HttpResponseMessage run (
			@HttpTrigger(name = "XmlErrorRetryTrigger",
			methods = {HttpMethod.GET},
			route = "xmlerror",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();

		try{
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");
			boolean deleteOnlyByKey = Boolean.parseBoolean(request.getQueryParameters().get("deleteOnlyByKey"));
			TableClient tableClient = getTableServiceClient().getTableClient(tableName);
			String message = null;
			if(partitionKey != null && rowKey != null){
				message = "Retrieve a single entity with [partitionKey="+partitionKey+"] [rowKey="+rowKey+"]";
				logger.info(message);
				TableEntity tableEntity = tableClient.getEntity(partitionKey, rowKey);
				process(logger, tableClient, tableEntity, deleteOnlyByKey);
			} else {
				message = "Retrieve all entity";
				logger.info(message);
				Iterator<TableEntity> itr = tableClient.listEntities().iterator();
				while (itr.hasNext()) {
					TableEntity tableEntity = itr.next();
					process(logger, tableClient, tableEntity, false);
				}
			}
			logger.info("Done processing events");
			return request
					.createResponseBuilder(HttpStatus.OK)
					.body(" SUCCESS. "+message)
					.build();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "[ALERT] Generic error at "+Instant.now(), e);
			return request
					.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("FAILED. "+ExceptionUtils.getStackTrace(e))
					.build();
		}
	}


	private static void process(Logger logger, TableClient tableClient, TableEntity tableEntity, boolean deleteOnlyByKey) {
		//cancella da FDR
		String fdr = (String)tableEntity.getProperty(AppConstant.columnFieldFdr);
		String pspId = (String)tableEntity.getProperty(AppConstant.columnFieldPspId);
		logger.info("Process fdr=["+fdr+"], pspId=["+pspId+"]");
		try {
			logger.info("Calling... internalDelete");
			getPspApi().internalDelete(fdr, pspId);
		} catch (ApiException e){
			logger.severe("Failed internalDelete. "+e.getResponseBody());
			//non faccio nulla perchè potrebbe essere che non esiste  e quindi è già fixato
		}

		// se errore è DELETE_BLOB_ERROR cancella solo il blob
		// altriemnti emulo una modifica per far triggerare l'altra function
		ErrorEnum errorEnum = ErrorEnum.valueOf((String)tableEntity.getProperty(AppConstant.columnFieldErrorType));
		String fileName = (String)tableEntity.getProperty(AppConstant.columnFieldFileName);
		if(ErrorEnum.DELETE_BLOB_ERROR == errorEnum){
			logger.info("Delete only blob");
			getBlobContainerClient().getBlobClient(fileName).delete();
		} else {
			if(deleteOnlyByKey){
				logger.info("NOT Modify trick for start trigger, delete only");
				getBlobContainerClient().getBlobClient(fileName).delete();
			} else {
				logger.info("Modify trick for start trigger");
				BinaryData binaryData = getBlobContainerClient().getBlobClient(fileName).downloadContent();
				getBlobContainerClient().getBlobClient(fileName).upload(binaryData, true);
			}
		}

		//cancello la riga degli errori
		logger.info("Delete row from error table");
		tableClient.deleteEntity(tableEntity);
	}


}
