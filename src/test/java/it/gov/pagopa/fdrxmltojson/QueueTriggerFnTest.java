package it.gov.pagopa.fdrxmltojson;

import com.azure.data.tables.TableClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.openapitools.client.ApiException;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class QueueTriggerFnTest {

	@Mock
	ExecutionContext context;

	@Mock
	private TableClient mockTableClient;

	@Mock
	private FdrXmlCommon fdrXmlCommon;

	@InjectMocks
	QueueTriggerFn queueTriggerFn;

	private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

	private static final Logger logger = Logger.getLogger("QueueTriggerFn-test-logger");

	@BeforeEach
	void setUp() {
		queueTriggerFn = new QueueTriggerFn(fdrXmlCommon);

		when(context.getLogger()).thenReturn(logger);

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
	void runOk() {
		// generating input
		String message = "{\"Type\":\"BlobTrigger\",\"FunctionId\":\"Host.Functions.BlobFdrXmlToJsonEventProcessor\",\"BlobType\":\"BlockBlob\",\"ContainerName\":\"fdr1-flows\",\"BlobName\":\"2025-02-2088888888888-235633854_27f28ae5-02ec-4845-90e8-6e1950b7a400.xml.zip\",\"ETag\":\"\\\"0x8DD51CD85C7A615\\\"\"}";
		long dequeueCount = 1;
		mockStorageAccountUtil.when(() -> StorageAccountUtil.getBlobContent(anyString()))
				.thenAnswer((Answer<BlobData>) invocation -> BlobData.builder()
						.fileName(invocation.getArgument(0))
						.metadata(Map.of("sessionId", "fake-sessionId"))
						.content(new byte[]{1, 2, 3})
						.build());

		Assertions.assertDoesNotThrow(() -> queueTriggerFn.run(message, dequeueCount, context));
	}

	@Test
	@SneakyThrows
	void runKo() {
		// generating input
		String message = "{\"Type\":\"BlobTrigger\",\"FunctionId\":\"Host.Functions.BlobFdrXmlToJsonEventProcessor\",\"BlobType\":\"BlockBlob\",\"ContainerName\":\"fdr1-flows\",\"BlobName\":\"2025-02-2088888888888-235633854_27f28ae5-02ec-4845-90e8-6e1950b7a400.xml.zip\",\"ETag\":\"\\\"0x8DD51CD85C7A615\\\"\"}";
		long dequeueCount = 6;

		mockStorageAccountUtil.when(() -> StorageAccountUtil.getBlobContent(anyString()))
				.thenAnswer((Answer<BlobData>) invocation -> BlobData.builder()
						.fileName(invocation.getArgument(0))
						.metadata(Map.of("sessionId", "fake-sessionId"))
						.content(new byte[]{1, 2, 3})
						.build());

		doThrow(new Exception("Simulated Exception")).when(fdrXmlCommon)
				.convertXmlToJson(any(), anyString(), any(), anyString(), anyLong());

		// execute logic
		Exception thrownException = assertThrows(Exception.class, () ->
				queueTriggerFn.run(message, dequeueCount, context));

		assertNotNull(thrownException);
	}

}
