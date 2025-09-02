package it.gov.pagopa.fdrxmltojson;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Context;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.model.QueueMessage;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Optional;

import static it.gov.pagopa.fdrxmltojson.util.FormatterUtil.messageFormat;
import static java.time.temporal.ChronoUnit.SECONDS;

@Slf4j
public class QueueTriggerFn {

    private FdrXmlCommon fdrXmlCommon;

    public QueueTriggerFn() {
        fdrXmlCommon = new FdrXmlCommon();
    }

    public QueueTriggerFn(FdrXmlCommon fdrXmlCommon) {
        this.fdrXmlCommon = fdrXmlCommon;
    }

    @FunctionName("QueueEventProcessor")
    public void run(
            @QueueTrigger(name = "queueTrigger",
                    connection = "STORAGE_ACCOUNT_CONN_STRING",
                    queueName = "webjobs-blobtrigger-poison") String message,
            @BindingName("DequeueCount") long dequeueCount,
            final ExecutionContext context) throws Exception {

        String operation = "Queue Trigger ";
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            MDC.put("invocationId", context.getInvocationId());
            log.info("Performing queue processing [retry attempt {}]", dequeueCount);

            QueueMessage queueMessage = objectMapper.readValue(message, QueueMessage.class);

            if (queueMessage != null &&
                    !queueMessage.getContainerName().isBlank() && queueMessage.getContainerName().equals(StorageAccountUtil.getFdr1FlowBlobContainerName()) &&
                    !queueMessage.getFileName().isBlank()
            ) {
                BlobData blobData = StorageAccountUtil.getBlobContent(queueMessage.getFileName());
                String sessionId = Optional.ofNullable(blobData.getMetadata().get("sessionId")).orElse("NA");

                MDC.put("sessionId", sessionId);
                MDC.put("fileName", blobData.getFileName());
                processEntity(dequeueCount, context, blobData, sessionId, operation);
            }
        } finally {
            MDC.clear();
        }
    }

    private void processEntity(
            long dequeueCount,
            ExecutionContext context,
            BlobData blobData,
            String sessionId,
            String operation
    ) throws Exception {
        try {
            fdrXmlCommon.convertXmlToJson(blobData.getContent(), dequeueCount, true);

            // if convertion is executed properly then removes the entry from fdr1conversion error table
            TableClient tableClient = StorageAccountUtil.getTableClient();
            ListEntitiesOptions listEntitiesOptions = new ListEntitiesOptions()
                    .setFilter(String.format("RowKey eq '%s'", sessionId));
            PagedIterable<TableEntity> entries = tableClient.listEntities(listEntitiesOptions, Duration.of(3, SECONDS), new Context("invocationId", context.getInvocationId()));
            if (entries != null) {
                for (TableEntity entry : entries) {
                    tableClient.deleteEntity(entry);
                }
            }

        } catch (Exception e) {
            String formattedMessage = messageFormat(sessionId, context.getInvocationId(), "NA", blobData.getFileName(), "%s error [retry attempt: %s]", operation, dequeueCount);
            log.error(formattedMessage, e);

            if (dequeueCount ==  getMaxRetryCount() - 1) {
                formattedMessage = messageFormat(sessionId, context.getInvocationId(), "NA", blobData.getFileName(), "[ALERT][FdrXmlToJson][LAST_RETRY] Performed last retry for blob processing");
                log.error(formattedMessage, e);
            }

            throw new Exception(formattedMessage, e);
        }
    }

    private Long getMaxRetryCount() {
        return Long.parseLong(System.getenv("MAX_RETRY_COUNT")); // it should be maxDequeueCount + 1
    }
}
