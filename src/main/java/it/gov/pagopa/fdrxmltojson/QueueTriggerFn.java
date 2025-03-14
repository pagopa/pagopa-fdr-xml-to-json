package it.gov.pagopa.fdrxmltojson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.fdrxmltojson.model.QueueMessage;
import it.gov.pagopa.fdrxmltojson.util.AppException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class QueueTriggerFn {

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

        logger = context.getLogger();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        QueueMessage queueMessage = objectMapper.readValue(message, QueueMessage.class);

        logger.log(Level.INFO, () -> String.format("QUEUE [dequeue count: %d][fileName: %s]", dequeueCount, queueMessage.getFileName()));

        throw new Exception();

    }

}
