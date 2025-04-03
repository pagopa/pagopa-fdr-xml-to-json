package it.gov.pagopa.fdrxmltojson.util;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import it.gov.pagopa.fdrxmltojson.model.BlobData;

import java.util.HashMap;
import java.util.Map;

public class StorageAccountUtil {

	private static final String STORAGE_ACCOUNT_CONN_STRING = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
	private static final String FDR1_FLOW_BLOB_CONTAINER_NAME = System.getenv("FDR1_FLOW_BLOB_CONTAINER_NAME");
	private static final String ERROR_TABLE_NAME = System.getenv("ERROR_TABLE_NAME");

	private static TableServiceClient tableServiceClient = null;
	private static BlobContainerClient blobContainerClient = null;

	public static String getFdr1FlowBlobContainerName() {
		return FDR1_FLOW_BLOB_CONTAINER_NAME;
	}

	public static TableClient getTableClient() {
		return getTableServiceClient().getTableClient(ERROR_TABLE_NAME);
	}

	public static BlobData getBlobContent(String fileName) {
		BlobContainerClient blobContainer = getBlobContainerClient();
		BlobClient blobClient = blobContainer.getBlobClient(fileName);
        Map<String, String> metadata = new HashMap<>(blobClient.getProperties().getMetadata());
		return BlobData.builder()
				.fileName(fileName)
				.metadata(metadata)
				.content(blobClient.downloadContent().toBytes())
				.build();
	}

    private static TableServiceClient getTableServiceClient() {
		if(tableServiceClient == null) {
			tableServiceClient = new TableServiceClientBuilder()
					.connectionString(STORAGE_ACCOUNT_CONN_STRING)
					.buildClient();
			tableServiceClient.createTableIfNotExists(ERROR_TABLE_NAME);
		}
		return tableServiceClient;
	}

	private static BlobContainerClient getBlobContainerClient() {
		if (blobContainerClient == null) {
			blobContainerClient = new BlobServiceClientBuilder()
					.connectionString(STORAGE_ACCOUNT_CONN_STRING)
					.buildClient()
					.getBlobContainerClient(FDR1_FLOW_BLOB_CONTAINER_NAME);
		}
		return blobContainerClient;
	}
}
