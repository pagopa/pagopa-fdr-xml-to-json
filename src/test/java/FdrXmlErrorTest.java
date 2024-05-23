import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.fdrxmltojson.FdrXmlError;
import it.gov.pagopa.fdrxmltojson.FdrXmlToJson;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import util.TestUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FdrXmlErrorTest {

    @Spy
    FdrXmlError fdrXmlError;

    @Mock
    ExecutionContext context;

    private static final Logger logger = Logger.getLogger("FdrXmlToJson-test-logger");

    final HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);

    static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

//    @Test
//    @SneakyThrows
//    void runOk() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
//
//        doReturn(responseMock).when(builder).build();
//        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
////        TableClient tableClient = mock(TableClient.class);
//        TableEntity tableEntity = mock(TableEntity.class);
//        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
////        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
////        when(tableClient.getEntity(anyString(), anyString())).thenReturn(tableEntity);
////        when(tableClient.listEntities().iterator()).thenReturn(pagedIterable.iterator());
//        setFinalStatic(FdrXmlError.class.getDeclaredField("tableServiceClient"), tableServiceClient);
//        setFinalStatic(FdrXmlError.class.getDeclaredField("tableName"), "errors");
//
//
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        BlobClient blobClient = mock(BlobClient.class);
//        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
//        setFinalStatic(FdrXmlError.class.getDeclaredField("blobContainerClient"), blobContainerClient);
//
//        // generating input
//        String eventInStringForm = TestUtil.readStringFromFile("events/event_ok_1.json");
//        List<String> events = new ArrayList<>();
//        events.add(eventInStringForm);
//        Map<String, Object>[] properties = new HashMap[1];
//        properties[0] = new HashMap<>();
//
////        doReturn("2024-01-01").when(request.getQueryParameters().get("partitionKey"));
////        doReturn("1").when(request.getQueryParameters().get("rowKey"));
////        doReturn("true").when(request.getQueryParameters().get("deleteOnlyByKey"));
//
//        // execute logic
//        fdrXmlError.run(request, context);
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_1() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
//
//        doReturn(responseMock).when(builder).build();
//        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        setFinalStatic(FdrXmlError.class.getDeclaredField("tableServiceClient"), tableServiceClient);
//        setFinalStatic(FdrXmlError.class.getDeclaredField("tableName"), "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        BlobClient blobClient = mock(BlobClient.class);
//        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
//        setFinalStatic(FdrXmlError.class.getDeclaredField("blobContainerClient"), blobContainerClient);
//
//        // generating input
//        String eventInStringForm = TestUtil.readStringFromFile("events/event_ok_1.json");
//        List<String> events = new ArrayList<>();
//        events.add(eventInStringForm);
//        Map<String, Object>[] properties = new HashMap[1];
//        properties[0] = new HashMap<>();
//
//        // execute logic
//        Assertions.assertThrows(Exception.class,
//                () -> fdrXmlError.run(request, context));
//    }

}
