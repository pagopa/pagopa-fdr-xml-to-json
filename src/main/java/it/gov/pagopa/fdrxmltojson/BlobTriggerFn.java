package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import java.util.*;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BlobTriggerFn {

	/*
	Executing this Azure Function in exponential retry, with steps:
	- retry 0: 0
	- retry 1: 10s
	- retry 2: 20s
	- retry 3: 40s
	- ...
	 */
    @FunctionName("BlobEventProcessor")
//	@ExponentialBackoffRetry(maxRetryCount = 5, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
    public void processNodoReEvent (
			@BlobTrigger(
					name = "xmlTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "%FDR1_FLOW_BLOB_CONTAINER_NAME%/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			@BindingName("Metadata") Map<String, String> blobMetadata,
			final ExecutionContext context) throws Exception {

		if (Boolean.parseBoolean(blobMetadata.getOrDefault("elaborate", "false"))) {

			String sessionId = blobMetadata.getOrDefault("sessionId", "NA");
			FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();

			fdrXmlCommon.convertXmlToJson(context, sessionId, content, fileName);

		}
    }

//	@FunctionalInterface
//	public interface SupplierWithApiException<T> {
//		T get() throws ApiException;
//	}

}

