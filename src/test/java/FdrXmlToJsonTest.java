import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdrxmltojson.AppException;
import it.gov.pagopa.fdrxmltojson.FdrXmlToJson;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.ErrorResponse;
import org.openapitools.client.model.GenericResponse;
import util.TestUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FdrXmlToJsonTest {

    @Spy
    FdrXmlToJson fdrXmlToJson;

    @Mock
    ExecutionContext context;

    private static final Logger logger = Logger.getLogger("FdrXmlToJson-test-logger");

    static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    @Test
    @SneakyThrows
    void runOk_withoutAdditionalProperties() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setMessage("OK");
        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

        // execute logic
        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void runKo_maxRetryCount() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setMessage("OK");
        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

        setFinalStatic(FdrXmlToJson.class.getDeclaredField("MAX_RETRY_COUNT"), -1);
        // execute logic
        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void runKo_errorBlobContainerLoad() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setMessage("OK");
        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

        setFinalStatic(FdrXmlToJson.class.getDeclaredField("MAX_RETRY_COUNT"), -1);
        // execute logic
        Assertions.assertThrows(Exception.class,
                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void runKo_errorBlobContainerDelete() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setMessage("OK");
        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

        // execute logic
        Assertions.assertThrows(Exception.class,
                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void runKo_pspHttpError() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(null);

        setFinalStatic(FdrXmlToJson.class.getDeclaredField("MAX_RETRY_COUNT"), -1);
        // execute logic
        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void runKo_pspErrorResponse() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        mockStatic(ErrorResponse.class);
        when(ErrorResponse.fromJson(anyString())).thenThrow(IOException.class);

        when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(new ApiException(400, "", new HashMap<>(), "{ \""));

        // execute logic
        Assertions.assertThrows(AppException.class,
                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(0)).internalAddPayment(anyString(), anyString(), any());
    }

    @Test
    @SneakyThrows
    void runKo_pspErrorResponseMalformed() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableServiceClient"), tableServiceClient);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("tableName"), "errors");

        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("blobContainerClient"), blobContainerClient);

        // generating input
        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");

        InternalPspApi pspApi = mock(InternalPspApi.class);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("pspApi"), pspApi);
        setFinalStatic(FdrXmlToJson.class.getDeclaredField("addPaymentRequestPartitionSize"), "10");

        when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(new ApiException(400, "", new HashMap<>(), "{ \"error\": \"OK\" }"));

        // execute logic
        Assertions.assertThrows(AppException.class,
                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));

        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
        verify(pspApi, times(0)).internalAddPayment(anyString(), anyString(), any());
    }

}
