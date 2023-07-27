package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.StorageAccount;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrXmlToJson {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */

    @FunctionName("BlobFdrXmlToJsonEventProcessor")
	@StorageAccount("AzureWebJobsStorage")
    public void processNodoReEvent (
			@BlobTrigger(
					name = "content",
					path = "myblob/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		logger.info("Java Blob trigger function processed a blob.\n Name: " + fileName + "\n Size: " + content.length + " Bytes");

        try {

        	logger.info("Done processing events");
		} catch (NullPointerException e) {
            logger.severe("NullPointerException exception on cosmos fdr-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Generic exception on cosmos fdr-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }

    }
}
