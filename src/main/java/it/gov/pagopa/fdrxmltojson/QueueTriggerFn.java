package it.gov.pagopa.fdrxmltojson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.model.QueueMessage;
import it.gov.pagopa.fdrxmltojson.util.AppException;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.gov.pagopa.fdrxmltojson.util.XMLUtil.messageFormat;

public class QueueTriggerFn {

    private static final String MAX_RETRY_COUNT = System.getenv("MAX_RETRY_COUNT"); // it should be maxDequeueCount + 1

    private static Logger logger;

    /*
	Executing this Azure Function in exponential retry, with steps:
	- retry 0: 0
	- retry 1: 10s
	- retry 2: 20s
	- retry 3: 40s
	- ...
	 */
    @FunctionName("QueueEventProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 5, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
    public void run(
            @QueueTrigger(name = "queueTrigger",
                    connection = "STORAGE_ACCOUNT_CONN_STRING",
                    queueName = "webjobs-blobtrigger-poison") String message,
            @BindingName("DequeueCount") long dequeueCount,
            final ExecutionContext context) throws Exception {

        String operation = "Queue Trigger ";
        logger = context.getLogger();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        logger.log(Level.INFO, () -> messageFormat("NA", context.getInvocationId(), null, "NA", "Performing queue processing [retry attempt %d]", dequeueCount));

        QueueMessage queueMessage = objectMapper.readValue(message, QueueMessage.class);

        BlobData blobData = StorageAccountUtil.getBlobContent(queueMessage.getFileName());
        FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();
        String sessionId = Optional.ofNullable(blobData.getMetadata().get("sessionId")).orElse("NA");
        try {
            fdrXmlCommon.convertXmlToJson(context, sessionId, blobData.getContent(), blobData.getFileName(), dequeueCount);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e, () -> messageFormat(sessionId, context.getInvocationId(), "NA", blobData.getFileName(), "%s error [retry attempt: %s]", operation, dequeueCount));

            if (dequeueCount ==  Long.parseLong(MAX_RETRY_COUNT) - 1) {
                logger.log(Level.SEVERE, () -> messageFormat(sessionId, context.getInvocationId(), "NA", blobData.getFileName(), "[ALERT][FdrXmlToJson][LAST_RETRY] Performed last retry for blob processing"));
            }

            throw new Exception();
        }

    }

}
