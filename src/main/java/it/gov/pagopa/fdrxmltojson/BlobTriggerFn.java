package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import jakarta.xml.bind.JAXBException;
import org.slf4j.MDC;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class BlobTriggerFn {

	@FunctionName("BlobEventProcessor")
    public void run (
			@BlobTrigger(
					name = "xmlToJsonTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "%FDR1_FLOW_BLOB_CONTAINER_NAME%/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			@BindingName("Metadata") Map<String, String> blobMetadata,
			final ExecutionContext context) throws XMLStreamException, JAXBException, IOException {

		if (Boolean.parseBoolean(blobMetadata.getOrDefault("elaborate", "false"))) {

			String sessionId = blobMetadata.getOrDefault("sessionId", "NA");
			FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();

			try {
				MDC.put("sessionId", sessionId);
				MDC.put("invocationId", context.getInvocationId());
				MDC.put("fileName", fileName);
				fdrXmlCommon.convertXmlToJson(content, 0, false);
			} finally {
				MDC.clear();
			}

		}
    }

}

