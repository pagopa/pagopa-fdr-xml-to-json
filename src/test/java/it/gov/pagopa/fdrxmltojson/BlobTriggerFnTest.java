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
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.ErrorResponse;
import org.openapitools.client.model.GenericResponse;
import org.powermock.reflect.Whitebox;
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
  private MockedStatic<ErrorResponse> mockErrorResponseUtil;

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
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);

    // execute logic
    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_genericException() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    when(pspApi.internalCreate(anyString(), anyString(), any()))
        .thenThrow(new ApiException("Test Exception"));

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_appErrorCodeValid() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_FLOW_ALREADY_CREATED);
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_noAppErrorCode() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_400_appErrorCodeNotValid() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"FDR-XXXX\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_create_apiException_404() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    ApiException apiException = mock(ApiException.class);
    Whitebox.setInternalState(apiException, "code", 404);

    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_genericException() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");

    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any()))
        .thenThrow(new ApiException("Test Exception"));

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeNotValid() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
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
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_noAppErrorCode() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_1() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
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
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_2() {
    // generating input
    byte[] content =
        TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
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
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_3() {
    // generating input
    byte[] content =
        TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
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
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_publish_apiException_400_notFound_1() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":404,\"httpStatusDescription\":\"Not Found\",\"errors\":[\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(404, "message", new HashMap<>(), responseBody);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(apiException);

    // execute logic
    blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context);

    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void runKo_pspHttpError_publish_apiException_400_notFound_2() {
    // generating input
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

    InternalPspApi pspApi = TestUtil.getPspApi();
    Map<String, String> metadata = TestUtil.getMetadata();
    String uuid = UUID.randomUUID().toString();

    GenericResponse genericResponse = new GenericResponse();
    genericResponse.setMessage("OK");
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
    ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(apiException);

    // execute logic
    Assertions.assertThrows(
        AppException.class, () -> blobTriggerFn.run(content, uuid, metadata, context));
  }
}
