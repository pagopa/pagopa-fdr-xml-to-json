package it.gov.pagopa.fdrxmltojson;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.azure.data.tables.TableClient;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.util.AppException;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;
import java.util.HashMap;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;
import org.openapitools.client.model.GenericResponse;
import org.slf4j.MDC;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class FdrXmlCommonTest {

  private final FdrXmlCommon fdrXmlCommon = new FdrXmlCommon();

  @Mock private TableClient mockTableClient;

  @SystemStub private EnvironmentVariables environmentVariables = new EnvironmentVariables();

  private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

  @BeforeEach
  void setUp() {
    TestUtil.setupEnvironmentVariables(environmentVariables);

    MDC.put("sessionId", UUID.randomUUID().toString());
    MDC.put("invocationId", "test-invocation");
    MDC.put("fileName", "test-file.xml.zip");

    mockStorageAccountUtil = mockStatic(StorageAccountUtil.class);
    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
    if (mockStorageAccountUtil != null) {
      mockStorageAccountUtil.close();
    }
    TestUtil.resetEnvironmentVariables(environmentVariables);
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenTryToDeleteTrueAndDeleteSucceeds() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(ok);

    fdrXmlCommon.convertXmlToJson(content, 0L, true);

    InOrder inOrder = inOrder(pspApi);
    inOrder.verify(pspApi).internalDelete(anyString(), anyString());
    inOrder.verify(pspApi).internalCreate(anyString(), anyString(), any());
    inOrder.verify(pspApi).internalAddPayment(anyString(), anyString(), any());
    inOrder.verify(pspApi).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenTryToDeleteTrueAndDeleteReturns404() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    ApiException notFound =
        new ApiException(
            404,
            "not found",
            new HashMap<>(),
            "{\"httpStatusCode\":404,\"appErrorCode\":\"FDR-3001\"}");

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenThrow(notFound);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(ok);

    fdrXmlCommon.convertXmlToJson(content, 0L, true);

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenTryToDeleteTrueAndDeleteFails() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    ApiException deleteError =
        new ApiException(
            500,
            "server error",
            new HashMap<>(),
            "{\"httpStatusCode\":500,\"appErrorCode\":\"FDR-9999\"}");

    when(pspApi.internalDelete(anyString(), anyString())).thenThrow(deleteError);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, never()).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenTryToDeleteTrueAndCreateReturnsFlowAlreadyCreated() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_FLOW_ALREADY_CREATED);

    ApiException createAlreadyExists = new ApiException(400, "message", new HashMap<>(), responseBody);

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(createAlreadyExists);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenTryToDeleteFalseAndCreateReturnsFlowAlreadyCreated() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_FLOW_ALREADY_CREATED);

    ApiException createAlreadyExists = new ApiException(400, "message", new HashMap<>(), responseBody);

    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(createAlreadyExists);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(ok);

    fdrXmlCommon.convertXmlToJson(content, 0L, false);

    verify(pspApi, never()).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenCreateReturnsBadRequestWithUnparseableBody() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    ApiException badRequest = new ApiException(400, "bad request", new HashMap<>(), "not-a-json");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(badRequest);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }

  @Test
  void convertXmlToJson_ko_whenContentIsNotGzip() {
    byte[] invalidContent = new byte[] {1, 2, 3, 4};

    assertThrows(
        IllegalArgumentException.class,
        () -> fdrXmlCommon.convertXmlToJson(invalidContent, 0L, true));
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenTryToDeleteFalseAndCreateSucceeds() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(ok);

    fdrXmlCommon.convertXmlToJson(content, 0L, false);

    verify(pspApi, never()).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenCreateReturnsNonBadRequestError() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    ApiException createError =
        new ApiException(
            500,
            "server error",
            new HashMap<>(),
            "{\"httpStatusCode\":500,\"appErrorCode\":\"FDR-9999\"}");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(createError);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenCreateReturnsBadRequestWithoutAppErrorCode() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";

    ApiException badRequest = new ApiException(400, "bad request", new HashMap<>(), responseBody);

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(badRequest);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenCreateReturnsBadRequestWithUnexpectedAppErrorCode() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"FDR-XXXX\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";

    ApiException badRequest = new ApiException(400, "bad request", new HashMap<>(), responseBody);

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(badRequest);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, never()).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenAddPaymentFailsWithGenericError() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any()))
        .thenThrow(new ApiException("Test Exception"));

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenAddPaymentAlreadyAddedAndFilteredChunkBecomesEmpty() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_PAYMENT_ALREADY_ADDED);

    ApiException paymentAlreadyAdded =
        new ApiException(400, "message", new HashMap<>(), responseBody);

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);

   // The add call fails because payment with index 1 is already present.
   // After filtering out the conflicting payment, the chunk becomes empty,
   // so no retry is needed and the flow can still be published.
    when(pspApi.internalAddPayment(anyString(), anyString(), any()))
        .thenThrow(paymentAlreadyAdded)
        .thenReturn(ok);

    when(pspApi.internalPublish(anyString(), anyString())).thenReturn(ok);

    fdrXmlCommon.convertXmlToJson(content, 0L, true);

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenAddPaymentAlreadyAddedIsNotRecoverable() {
    byte[] content =
        TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    String responseBody =
        String.format(
            "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}",
            AppConstant.FDR_PAYMENT_ALREADY_ADDED);

    ApiException paymentAlreadyAdded =
        new ApiException(400, "message", new HashMap<>(), responseBody);

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);

    // Every add attempt fails with the same already-added conflict.
    // The internal logic retries up to the configured limit, then throws AppException.
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(paymentAlreadyAdded);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());

    // Expected attempts:
    // initial call + retry 1 + retry 2 = 3 total invocations
    verify(pspApi, times(3)).internalAddPayment(anyString(), anyString(), any());

    verify(pspApi, never()).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ok_whenPublishReturns404() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    ApiException notFound = new ApiException(404, "not found", new HashMap<>(), "");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(notFound);

    fdrXmlCommon.convertXmlToJson(content, 0L, true);

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
  
  @Test
  @SneakyThrows
  void convertXmlToJson_ko_whenPublishFailsWithNon404Error() {
    byte[] content = TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

    InternalPspApi pspApi = TestUtil.getPspApi();

    GenericResponse ok = new GenericResponse();
    ok.setMessage("OK");

    ApiException publishError =
        new ApiException(400, "bad request", new HashMap<>(), "{\"httpStatusCode\":400}");

    when(pspApi.internalDelete(anyString(), anyString())).thenReturn(ok);
    when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(ok);
    when(pspApi.internalPublish(anyString(), anyString())).thenThrow(publishError);

    assertThrows(AppException.class, () -> fdrXmlCommon.convertXmlToJson(content, 0L, true));

    verify(pspApi, times(1)).internalDelete(anyString(), anyString());
    verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
    verify(pspApi, times(1)).internalPublish(anyString(), anyString());
  }
}