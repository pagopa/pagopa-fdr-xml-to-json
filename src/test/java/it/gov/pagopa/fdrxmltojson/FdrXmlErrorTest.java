package it.gov.pagopa.fdrxmltojson;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.InternalPspApi;

import com.azure.core.util.IterableStream;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.FdR3ClientUtil;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import it.gov.pagopa.fdrxmltojson.util.FakeHttpResponseBuilder;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class FdrXmlErrorTest {

	@InjectMocks
	private FdrXmlError fdrXmlError;

	@Mock
	private ExecutionContext context;

	@Mock
	private HttpRequestMessage<Optional<String>> request;

	@Mock
	private Logger logger;

	@Mock
	private TableClient mockTableClient;

	@Mock
	private FdrXmlCommon fdrXmlCommon;

	private MockedStatic<FdR3ClientUtil> mockFdR3ClientUtil;

	private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

	@SystemStub private EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@BeforeEach
	void setUp() throws ApiException {
		// Simulate environment variables
		TestUtil.setupEnvironmentVariables(environmentVariables);

		when(context.getLogger()).thenReturn(logger);
		lenient().when(context.getInvocationId()).thenReturn("test-invocation");

		// Mock response builder
		when(request.createResponseBuilder(any(HttpStatus.class)))
		.thenAnswer(invocation -> new FakeHttpResponseBuilder().status(invocation.getArgument(0)));

		// Mock static methods of StorageAccountUtil
		mockStorageAccountUtil = mockStatic(StorageAccountUtil.class);
		mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);
		mockStorageAccountUtil.when(() -> StorageAccountUtil.getBlobContent(anyString()))
		.thenAnswer((Answer<BlobData>) invocation -> BlobData.builder()
				.fileName(invocation.getArgument(0))
				.metadata(Map.of("sessionId", "fake-sessionId"))
				.content(TestUtil.getFileContent("xmlcontent/nodoInviaFlussoRendicontazione.xml"))
				.build());

		// Mock table entities
		List<TableEntity> entities = new ArrayList<>();
		for (int i = 1; i <= 2; i++) {
			TableEntity entity = new TableEntity("testPartition", "row" + i)
					.addProperty("name", "EntityName" + i)
					.addProperty("value", i * 10)
					.addProperty("status", "active")
					.addProperty("fileName", "fake-file-name")
					.addProperty("fdr", "fake-fdr")
					.addProperty("pspId", "fake-pspId");
			entities.add(entity);
		}
		lenient().when(mockTableClient.listEntities())
				.thenReturn(TestUtil.createPagedIterable(new IterableStream<>(entities)));

		lenient().when(mockTableClient.listEntities(any(), any(), any()))
				.thenReturn(TestUtil.createPagedIterable(new IterableStream<>(entities)));

		lenient().when(mockTableClient.getEntity(anyString(), anyString())).thenReturn(entities.get(0));

		String responseBody="{\"httpStatusCode\":404,\"httpStatusDescription\":\"Not Found\",\"appErrorCode\":\"FDR-3001\",\"errors\":[{\"message\":\"Flow with ID [mock-flow-id] not found.\"}]}";
		Map<String, List<String>> responseHeaders = new HashMap<>(){
			private static final long serialVersionUID = 1L;
		{
			put("content-type", Arrays.asList("application/json", "charset=UTF-8"));
		}};
		ApiException apiException = new ApiException(404, responseHeaders, responseBody);
		// Mock static methods of FdR3ClientUtil
		InternalPspApi mockInternalPspApi = mock(InternalPspApi.class);
		mockFdR3ClientUtil = mockStatic(FdR3ClientUtil.class);
		mockFdR3ClientUtil.when(FdR3ClientUtil::getPspApi).thenReturn(mockInternalPspApi);
		lenient().when(mockInternalPspApi.internalDelete(anyString(), anyString())).thenThrow(apiException);
	}

	@AfterEach
	void tearDown() {
		if (mockStorageAccountUtil != null) {
			mockStorageAccountUtil.close();
		}
		if (mockFdR3ClientUtil != null) {
			mockFdR3ClientUtil.close();
		}

		TestUtil.resetEnvironmentVariables(environmentVariables);
	}

	@Test
	void testRun_withValidPartitionAndRowKey() {
		when(request.getQueryParameters()).thenReturn(Map.of("partitionKey", "testPartition", "rowKey", "testRow"));

		HttpResponseMessage response = fdrXmlError.run(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatus());
	}

	@Test
	void testRun_withoutPartitionAndRowKey() {
		when(request.getQueryParameters()).thenReturn(Map.of());

		HttpResponseMessage response = fdrXmlError.run(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatus());
	}

	@Test
	void testRun_withException() {
		when(request.getQueryParameters()).thenThrow(new RuntimeException("Test Exception"));

		HttpResponseMessage response = fdrXmlError.run(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
	}

	@Test
	@SneakyThrows
	void testRun_withException_2() {
		when(request.getQueryParameters()).thenReturn(Map.of("partitionKey", "testPartition", "rowKey", "testRow"));
		doThrow(new IOException("Simulated Exception")).when(fdrXmlCommon).convertXmlToJson(any(), anyString(), any(), anyString(), anyLong(), anyBoolean());

		HttpResponseMessage response = fdrXmlError.run(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
	}

	@Test
	void testRunErrorRecovery_withValidPartitionAndRowKeys() {
		when(request.getBody()).thenReturn(Optional.of("{\"partitionKey\":\"2025-03-26\",\"rowKeys\":[\"0f9aa641-5cd7-474d-a40b-9b815aa02629\",\"1652bccb-37be-4c25-94bb-45cbc711be56\"]}"));
		HttpResponseMessage response = fdrXmlError.runErrorRecovery(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatus());
	}

	@Test
	void testRunErrorRecovery_withoutPartition() {
		when(request.getBody()).thenReturn(Optional.of("{\"partitionKey\":\"\",\"rowKeys\":[\"0f9aa641-5cd7-474d-a40b-9b815aa02629\",\"1652bccb-37be-4c25-94bb-45cbc711be56\"]}"));
		HttpResponseMessage response = fdrXmlError.runErrorRecovery(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
	}

	@Test
	void testRunErrorRecovery_withValidPartitionAndNoRowKeys() {
		when(request.getBody()).thenReturn(Optional.of("{\"partitionKey\":\"2025-03-26\",\"rowKeys\":[]}"));
		HttpResponseMessage response = fdrXmlError.runErrorRecovery(request, context);

		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatus());
	}

}