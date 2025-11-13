package it.gov.pagopa.fdrxmltojson;

import static org.mockito.Mockito.*;

import com.azure.data.tables.TableClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.util.AppException;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.GenericResponse;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BlobTriggerFnTest {

  @Spy BlobTriggerFn blobTriggerFn;

  @Mock ExecutionContext context;

  @Mock private TableClient mockTableClient;

  @SystemStub private EnvironmentVariables environmentVariables = new EnvironmentVariables();

  private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

  @BeforeEach
  void setUp() {
    TestUtil.setupEnvironmentVariables(environmentVariables);

    lenient().when(context.getInvocationId()).thenReturn("test-invocation");

    // Mock static methods of StorageAccountUtil
    mockStorageAccountUtil = mockStatic(StorageAccountUtil.class);
    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);
  }

  @AfterEach
  void tearDown() {
    if (mockStorageAccountUtil != null) {
      mockStorageAccountUtil.close();
    }
    TestUtil.resetEnvironmentVariables(environmentVariables);
  }

  @Test
  @SneakyThrows
  void runOk_withoutAdditionalProperties() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    InOrder inOrder = inOrder(pspApi);
    inOrder.verify(pspApi).internalDelete(anyString(), anyString());
    inOrder.verify(pspApi).internalCreate(anyString(), anyString(), any());
    inOrder.verify(pspApi).internalAddPayment(anyString(), anyString(), any());
    inOrder.verify(pspApi).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_genericException() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any()))
        .thenThrow(new ApiException("Test Exception"));

    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_appErrorCodeValid() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_FLOW_ALREADY_CREATED);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    Assertions.assertThrows(
        AppException.class,
        () -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_noAppErrorCode() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();
    
    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_appErrorCodeNotValid() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();
    
    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"FDR-XXXX\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_404() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();
    
    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    
    ApiException apiException = new ApiException(404, "message", new HashMap<>(), "");

    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_genericException() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any()))
        .thenThrow(new ApiException("Test Exception"));

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeNotValid() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_FLOW_NOT_FOUND);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_noAppErrorCode() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_1() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_PAYMENT_ALREADY_ADDED);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_2() {
    // generating input
    byte[] content =
        TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_PAYMENT_ALREADY_ADDED);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(3)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_3() {
    // generating input
    byte[] content =
        TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        String.format(
            "{\"httpStatusCode\": 400,\"httpStatusDescription\": \"Bad Request\",\"appErrorCode\": \"%s\"}",
            AppConstant.FDR_PAYMENT_ALREADY_ADDED);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_publish_apiException_400_notFound_1() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":404,\"httpStatusDescription\":\"Not Found\",\"errors\":[\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(404, "message", new HashMap<>(), responseBody);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(apiException);

    // execute logic
    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_publish_apiException_400_notFound_2() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    
    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
    
    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void runOk_whenPreventiveDeleteReturns404() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    ApiException deleteNotFound =
        new ApiException(
            404,
            "not found",
            new HashMap<>(),
            "{\"httpStatusCode\":404,\"appErrorCode\":\"FDR-3001\"}");

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenThrow(deleteNotFound);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void runKo_whenPreventiveDeleteFails() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    ApiException deleteError =
        new ApiException(
            500,
            "server error",
            new HashMap<>(),
            "{\"httpStatusCode\":500,\"appErrorCode\":\"FDR-9999\"}");

    when(pspApi.internalDelete(anyString(), anyString())).thenThrow(deleteError);

    Assertions.assertThrows(
        AppException.class,
        () -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, never()).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void runKo_whenCreateReturnsBadRequestWithUnparseableBody() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    ApiException apiException = new ApiException(400, "message", new HashMap<>(), "not-a-json-body");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(genericResponse);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    Assertions.assertThrows(
        AppException.class,
        () -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  void runKo_whenContentIsNotGzip() {
    byte[] invalidContent = new byte[] {1, 2, 3, 4};
    String blobName = UUID.randomUUID().toString();
    Map<String, String> metadata = TestUtil.getMetadata();

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> blobTriggerFn.run(invalidContent, blobName, metadata, context));
  }
}
