package it.gov.pagopa.fdrxmltojson;
//import com.azure.data.tables.TableClient;
//import com.azure.data.tables.TableServiceClient;
//import com.azure.storage.blob.BlobClient;
//import com.azure.storage.blob.BlobContainerClient;
//import com.microsoft.azure.functions.ExecutionContext;
//import it.gov.pagopa.fdrxmltojson.util.AppException;
//import it.gov.pagopa.fdrxmltojson.FdrXmlToJson;
//import lombok.SneakyThrows;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.openapitools.client.ApiException;
//import org.openapitools.client.api.InternalPspApi;
//import org.openapitools.client.model.ErrorResponse;
//import org.openapitools.client.model.GenericResponse;
//import org.powermock.reflect.Whitebox;
//import util.TestUtil;
//
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
//import java.util.UUID;
//import java.util.logging.Logger;
//
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class FdrXmlToJsonTest {
//
//    @Spy
//    FdrXmlToJson fdrXmlToJson;
//
//    @Mock
//    ExecutionContext context;
//
//    private static final Logger logger = Logger.getLogger("FdrXmlToJson-test-logger");
//
//    @Test
//    @SneakyThrows
//    void runOk_withoutAdditionalProperties() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient", tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName", "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        BlobClient blobClient = mock(BlobClient.class);
//        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient", blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi", pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize", "10");
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        // execute logic
//        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_maxRetryCount() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        BlobClient blobClient = mock(BlobClient.class);
//        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize",  "10");
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        Whitebox.setInternalState(FdrXmlToJson.class, "MAX_RETRY_COUNT",  -1);
//        // execute logic
//        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_errorBlobContainerLoad() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName",  "errors");
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize",  "10");
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        Whitebox.setInternalState(FdrXmlToJson.class, "MAX_RETRY_COUNT",  -1);
//        // execute logic
//        Assertions.assertThrows(Exception.class,
//                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_errorBlobContainerDelete() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        TableClient tableClient = mock(TableClient.class);
//        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize",  "10");
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        // execute logic
//        Assertions.assertThrows(Exception.class,
//                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_pspHttpError() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        BlobClient blobClient = mock(BlobClient.class);
//        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize",  "10");
//
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(null);
//
//        Whitebox.setInternalState(FdrXmlToJson.class, "MAX_RETRY_COUNT",  -1);
//        // execute logic
//        fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context);
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_pspErrorResponse() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        TableClient tableClient = mock(TableClient.class);
//        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize",  "10");
//
//        mockStatic(ErrorResponse.class);
//        when(ErrorResponse.fromJson(anyString())).thenThrow(IOException.class);
//
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(new ApiException(400, "", new HashMap<>(), "{ \""));
//
//        // execute logic
//        Assertions.assertThrows(AppException.class,
//                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(0)).internalAddPayment(anyString(), anyString(), any());
//    }
//
//    @Test
//    @SneakyThrows
//    void runKo_pspErrorResponseMalformed() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        TableClient tableClient = mock(TableClient.class);
//        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableServiceClient", tableServiceClient);
//        Whitebox.setInternalState(FdrXmlToJson.class, "tableName", "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "blobContainerClient", blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(FdrXmlToJson.class, "pspApi", pspApi);
//        Whitebox.setInternalState(FdrXmlToJson.class, "addPaymentRequestPartitionSize", "10");
//
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(new ApiException(400, "", new HashMap<>(), "{ \"error\": \"OK\" }"));
//
//        // execute logic
//        Assertions.assertThrows(AppException.class,
//                () -> fdrXmlToJson.processNodoReEvent(xml.getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString(), context));
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(0)).internalAddPayment(anyString(), anyString(), any());
//    }
//
//}
