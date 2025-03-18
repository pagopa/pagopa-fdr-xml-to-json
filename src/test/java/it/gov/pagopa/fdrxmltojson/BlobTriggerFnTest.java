package it.gov.pagopa.fdrxmltojson;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdrxmltojson.model.AppConstant;
import it.gov.pagopa.fdrxmltojson.util.AppException;
import it.gov.pagopa.fdrxmltojson.util.FdR3ClientUtil;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
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
import org.openapitools.client.model.GenericResponse;
import org.powermock.reflect.Whitebox;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BlobTriggerFnTest {

	@Spy
	BlobTriggerFn blobTriggerFn;

	@Mock
	ExecutionContext context;

	@Mock
	private TableClient mockTableClient;

//	@Mock
//	private TableClient mockTableClient;

	@SystemStub
	private EnvironmentVariables environmentVariables = new EnvironmentVariables();

	private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

	private static final Logger logger = Logger.getLogger("BlobTriggerFn-test-logger");

	@BeforeEach
	void setUp() throws ApiException {
		TestUtil.setupEnvironmentVariables(environmentVariables);

		when(context.getLogger()).thenReturn(logger);
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
	}

	@Test
	@SneakyThrows
	void runOk_withoutAdditionalProperties() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		InternalPspApi pspApi = getPspApi();

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

//    @Test
//    @SneakyThrows
//    void runKo_maxRetryCount() {
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//		byte[] content = TestUtil.gzipCompress(xml.getBytes(StandardCharsets.UTF_8));
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "pspApi",  pspApi);
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        Whitebox.setInternalState(BlobTriggerFn.class, "MAX_RETRY_COUNT",  -1);
//
//		// execute logic
//		blobTriggerFn.run(content, UUID.randomUUID().toString(), getMetadata(), context);
//
//        verify(pspApi, times(1)).internalCreate(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalAddPayment(anyString(), anyString(), any());
//        verify(pspApi, times(1)).internalPublish(anyString(), anyString());
//    }

//    @Test
//    @SneakyThrows
//    void runKo_errorBlobContainerLoad() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableName",  "errors");
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "pspApi",  pspApi);
//
//        GenericResponse genericResponse = new GenericResponse();
//        genericResponse.setMessage("OK");
//        when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);
//        when(pspApi.internalPublish(anyString(), anyString())).thenReturn(genericResponse);
//
//        Whitebox.setInternalState(BlobTriggerFn.class, "MAX_RETRY_COUNT",  -1);
//
//		// execute logic
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
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(BlobTriggerFn.class, "addPaymentRequestPartitionSize",  "10");
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
    @Test
    @SneakyThrows
    void runKo_pspHttpError_create_genericException() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

        InternalPspApi pspApi = getPspApi();

		when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(new ApiException("Test Exception"));

		// execute logic
		Assertions.assertThrows(AppException.class,
                () -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
    }

	@Test
	@SneakyThrows
	void runKo_pspHttpError_create_apiException_400_appErrorCodeValid() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		String responseBody = String.format("{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}", AppConstant.FDR_FLOW_ALREADY_CREATED);
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
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		String responseBody = "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_create_apiException_400_appErrorCodeNotValid() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		String responseBody = "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"FDR-XXXX\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_create_apiException_404() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		ApiException apiException = mock(ApiException.class);
		Whitebox.setInternalState(apiException, "code", 404);

		when(pspApi.internalCreate(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_add_genericException() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");

		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(new ApiException("Test Exception"));

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_add_apiException_400_appErrorCodeNotValid() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = String.format("{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}", AppConstant.FDR_FLOW_NOT_FOUND);
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_add_apiException_400_noAppErrorCode() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_1() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = String.format("{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}", AppConstant.FDR_PAYMENT_ALREADY_ADDED);
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
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = String.format("{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"appErrorCode\":\"%s\",\"errors\":[{\"path\":\"[1]\",\"message\":\"<detail.message>\"}]}", AppConstant.FDR_PAYMENT_ALREADY_ADDED);
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_add_apiException_400_appErrorCodeValid_3() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione_morePayments.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = String.format("{\"httpStatusCode\": 400,\"httpStatusDescription\": \"Bad Request\",\"appErrorCode\": \"%s\"}", AppConstant.FDR_PAYMENT_ALREADY_ADDED);
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

	@Test
	@SneakyThrows
	void runKo_pspHttpError_publish_apiException_400_notFound_1() {
		// generating input
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = "{\"httpStatusCode\":404,\"httpStatusDescription\":\"Not Found\",\"errors\":[\"message\":\"<detail.message>\"}]}";
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
		byte[] content = getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml");

		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);

		InternalPspApi pspApi = getPspApi();

		GenericResponse genericResponse = new GenericResponse();
		genericResponse.setMessage("OK");
		when(pspApi.internalCreate(anyString(), anyString(), any())).thenReturn(genericResponse);
		when(pspApi.internalAddPayment(anyString(), anyString(), any())).thenReturn(genericResponse);

		String responseBody = "{\"httpStatusCode\":400,\"httpStatusDescription\":\"Bad Request\",\"errors\":[{\"path\":\"<detail.path.if-exist>\",\"message\":\"<detail.message>\"}]}";
		ApiException apiException = new ApiException(400, "message", new HashMap<>(), responseBody);
		when(pspApi.internalPublish(anyString(), anyString())).thenThrow(apiException);

		// execute logic
		Assertions.assertThrows(AppException.class,
				() -> blobTriggerFn.run(content, UUID.randomUUID().toString(), TestUtil.getMetadata(), context));
	}

//    @Test
//    @SneakyThrows
//    void runKo_pspErrorResponse() {
//        // mocking objects
//        when(context.getLogger()).thenReturn(logger);
//
//        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
//        TableClient tableClient = mock(TableClient.class);
//        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableServiceClient",  tableServiceClient);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableName",  "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "blobContainerClient",  blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "pspApi",  pspApi);
//        Whitebox.setInternalState(BlobTriggerFn.class, "addPaymentRequestPartitionSize",  "10");
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
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableServiceClient", tableServiceClient);
//        Whitebox.setInternalState(BlobTriggerFn.class, "tableName", "errors");
//
//        BlobContainerClient blobContainerClient = mock(BlobContainerClient.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "blobContainerClient", blobContainerClient);
//
//        // generating input
//        String xml = TestUtil.readStringFromFile("xmlcontent/nodoInviaFlussoRendicontazione.xml");
//
//        InternalPspApi pspApi = mock(InternalPspApi.class);
//        Whitebox.setInternalState(BlobTriggerFn.class, "pspApi", pspApi);
//        Whitebox.setInternalState(BlobTriggerFn.class, "addPaymentRequestPartitionSize", "10");
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

	private byte[] getFileContent(String fileName) throws IOException {
		String xml = TestUtil.readStringFromFile(fileName);
		return TestUtil.gzipCompress(xml.getBytes(StandardCharsets.UTF_8));
	}

	private InternalPspApi getPspApi() {
		InternalPspApi pspApi = mock(InternalPspApi.class);
		Whitebox.setInternalState(FdR3ClientUtil.class, "pspApi", pspApi);
		return pspApi;
	}


}
