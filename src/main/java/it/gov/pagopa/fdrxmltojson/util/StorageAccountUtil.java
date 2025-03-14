package it.gov.pagopa.fdrxmltojson.util;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;

public class StorageAccountUtil {

    private static final String STORAGE_ACCOUNT_CONN_STRING = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
    private static final String ERROR_TABLE_NAME = System.getenv("ERROR_TABLE_NAME");

    private static TableServiceClient tableServiceClient = null;

    public static TableClient getTableClient() {
        return getTableServiceClient().getTableClient(ERROR_TABLE_NAME);
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

}
