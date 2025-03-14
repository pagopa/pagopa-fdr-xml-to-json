package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableServiceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import java.util.*;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {



	private static final String storageConnString = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
	private static final String blobContainerName = System.getenv("BLOB_CONTAINER_NAME");

	private static Logger logger;

	private static InternalPspApi pspApi = null;
	private static TableServiceClient tableServiceClient = null;

	private static BlobContainerClient blobContainerClient;

//	private static final Map<StTipoIdentificativoUnivoco, SenderTypeEnum> typeMap = new LinkedHashMap<>();
//	private static final Map<String, PaymentStatusEnum> payStatusMap = new LinkedHashMap<>();
//	static {
//		typeMap.put(StTipoIdentificativoUnivoco.G, SenderTypeEnum.LEGAL_PERSON);
//		typeMap.put(StTipoIdentificativoUnivoco.A, SenderTypeEnum.ABI_CODE);
//		typeMap.put(StTipoIdentificativoUnivoco.B, SenderTypeEnum.BIC_CODE);
//
//		payStatusMap.put("0", PaymentStatusEnum.EXECUTED);
//		payStatusMap.put("3", PaymentStatusEnum.REVOKED);
//		payStatusMap.put("4", PaymentStatusEnum.STAND_IN);
//		payStatusMap.put("8", PaymentStatusEnum.STAND_IN_NO_RPT);
//		payStatusMap.put("9", PaymentStatusEnum.NO_RPT);
//	}

//	private static InternalPspApi getPspApi() {
//		if(pspApi==null){
//			ApiClient apiClient = new ApiClient();
//			apiClient.setApiKey(fdrNewApiKey);
//			pspApi = new InternalPspApi(apiClient);
//			pspApi.setCustomBaseUrl(fdrNewBaseUrl);
//		}
//		return pspApi;
//	}

//	private static TableServiceClient getTableServiceClient(){
//		if(tableServiceClient==null){
//			tableServiceClient = new TableServiceClientBuilder()
//					.connectionString(tableStorageConnString)
//					.buildClient();
//			tableServiceClient.createTableIfNotExists(tableName);
//		}
//		return tableServiceClient;
//	}

//	private static BlobContainerClient getBlobContainerClient(){
//		if(blobContainerClient==null){
//			BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(storageConnString).buildClient();
//			blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(blobContainerName);
//		}
//		return blobContainerClient;
//	}

	/*
	Executing this Azure Function in exponential retry, with steps:
	- retry 0: 0
	- retry 1: 10s
	- retry 2: 20s
	- retry 3: 40s
	- ...
	 */
    @FunctionName("BlobFdrXmlToJsonEventProcessor")
//	@ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
    public void processNodoReEvent (
			@BlobTrigger(
					name = "xmlTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "%FDR1_FLOW_BLOB_CONTAINER_NAME%/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			@BindingName("Metadata") Map<String, String> blobMetadata,
			final ExecutionContext context) {

		if (Boolean.parseBoolean(blobMetadata.getOrDefault("elaborate", "false"))) {

			String sessionId = blobMetadata.getOrDefault("sessionId", "NA");
			FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();

			fdrXmlCommon.convertXmlToJson(context, sessionId, content, fileName);

		}
    }

	@FunctionalInterface
	public interface SupplierWithApiException<T> {
		T get() throws ApiException;
	}

}

