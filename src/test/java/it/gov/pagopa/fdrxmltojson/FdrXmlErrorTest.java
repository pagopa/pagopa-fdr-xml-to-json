package it.gov.pagopa.fdrxmltojson;

import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.FdrXmlError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.*;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdrXmlErrorTest {

    @Spy
    FdrXmlError fdrXmlError;

    @Mock
    ExecutionContext context;

    private static final Logger logger = Logger.getLogger("FdrXmlError-test-logger");

    final HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);

    @Test
    @SneakyThrows
    void runOk() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        Whitebox.setInternalState(FdrXmlError.class, "tableName", "errors");
        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrXmlError.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        TableEntity tableEntity = new TableEntity("2024-01-01", "1");
        Map<String, Object> tableEntityMap = new HashMap<>();
        tableEntityMap.put(AppConstant.columnFieldFdr, "fdr");
        tableEntityMap.put(AppConstant.columnFieldPspId, "pspId");
        tableEntityMap.put(AppConstant.columnFieldErrorType, "DELETE_BLOB_ERROR");
        tableEntityMap.put(AppConstant.columnFieldFileName, "provaFileName");
        tableEntity.setProperties(tableEntityMap);
        Iterator<TableEntity> mockIterator = List.of(tableEntity).iterator();
        when(pagedIterable.iterator()).thenReturn(mockIterator);

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        Whitebox.setInternalState(FdrXmlError.class, "blobContainerClient", blobContainerClient);

        // generating input
        request.getQueryParameters().put("partitionKey", "2024-01-01");
        request.getQueryParameters().put("rowKey", "1");
        request.getQueryParameters().put("deleteOnlyByKey", "true");

        // execute logic
        Assertions.assertThrows(Exception.class, () -> fdrXmlError.run(request, context));
    }

    @Test
    @SneakyThrows
    void runOk_2() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        Whitebox.setInternalState(FdrXmlError.class, "tableName", "errors");
        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrXmlError.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        TableEntity tableEntity = new TableEntity("2024-01-01", "1");
        Map<String, Object> tableEntityMap = new HashMap<>();
        tableEntityMap.put(AppConstant.columnFieldFdr, "fdr");
        tableEntityMap.put(AppConstant.columnFieldPspId, "pspId");
        tableEntityMap.put(AppConstant.columnFieldErrorType, "GENERIC_ERROR");
        tableEntityMap.put(AppConstant.columnFieldFileName, "provaFileName");
        tableEntity.setProperties(tableEntityMap);
        Iterator<TableEntity> mockIterator = List.of(tableEntity).iterator();
        when(pagedIterable.iterator()).thenReturn(mockIterator);

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        Whitebox.setInternalState(FdrXmlError.class, "blobContainerClient", blobContainerClient);

        // generating input
        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        request.getQueryParameters().put("partitionKey", "2024-01-01");
        request.getQueryParameters().put("rowKey", "1");
        request.getQueryParameters().put("deleteOnlyByKey", "true");

        // execute logic
        Assertions.assertThrows(Exception.class, () -> fdrXmlError.run(request, context));
    }

    @Test
    @SneakyThrows
    void runKo_1() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        Whitebox.setInternalState(FdrXmlError.class, "tableName", "errors");
        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrXmlError.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        TableEntity tableEntity = new TableEntity("2024-01-01", "1");
        Map<String, Object> tableEntityMap = new HashMap<>();
        tableEntityMap.put(AppConstant.columnFieldFdr, "fdr");
        tableEntityMap.put(AppConstant.columnFieldPspId, "pspId");
        tableEntityMap.put(AppConstant.columnFieldErrorType, "GENERIC_ERROR");
        tableEntityMap.put(AppConstant.columnFieldFileName, "provaFileName");
        tableEntity.setProperties(tableEntityMap);
        Iterator<TableEntity> mockIterator = List.of(tableEntity).iterator();
        when(pagedIterable.iterator()).thenReturn(mockIterator);

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        Whitebox.setInternalState(FdrXmlError.class, "blobContainerClient", blobContainerClient);

        // generating input
        request.getQueryParameters().put("partitionKey", "2024-01-01");
        request.getQueryParameters().put("rowKey", "1");
        request.getQueryParameters().put("deleteOnlyByKey", "false");

        // execute logic
        Assertions.assertThrows(Exception.class, () -> fdrXmlError.run(request, context));
    }

}
