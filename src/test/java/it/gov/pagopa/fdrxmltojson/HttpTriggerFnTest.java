package it.gov.pagopa.fdrxmltojson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.azure.core.util.IterableStream;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import it.gov.pagopa.fdrxmltojson.util.FakeHttpResponseBuilder;
import it.gov.pagopa.fdrxmltojson.util.FdR3ClientUtil;
import it.gov.pagopa.fdrxmltojson.util.StorageAccountUtil;
import it.gov.pagopa.fdrxmltojson.util.TestUtil;
import java.util.*;
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
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class HttpTriggerFnTest {

  @InjectMocks private HttpTriggerFn httpTriggerFn;

  @Mock private ExecutionContext context;

  @Mock private HttpRequestMessage<Optional<String>> request;

  @Mock private TableClient mockTableClient;

  private MockedStatic<FdR3ClientUtil> mockFdR3ClientUtil;

  private MockedStatic<StorageAccountUtil> mockStorageAccountUtil;

  @SystemStub private EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @BeforeEach
  void setUp() throws ApiException {
    // Simulate environment variables
    TestUtil.setupEnvironmentVariables(environmentVariables);

    lenient().when(context.getInvocationId()).thenReturn("test-invocation");

    // Mock response builder
    when(request.createResponseBuilder(any(HttpStatus.class)))
        .thenAnswer(invocation -> new FakeHttpResponseBuilder().status(invocation.getArgument(0)));

    // Mock static methods of StorageAccountUtil
    mockStorageAccountUtil = mockStatic(StorageAccountUtil.class);
    mockStorageAccountUtil.when(StorageAccountUtil::getTableClient).thenReturn(mockTableClient);
    mockStorageAccountUtil
        .when(() -> StorageAccountUtil.getBlobContent(anyString()))
        .thenAnswer(
            (Answer<BlobData>)
                invocation ->
                    BlobData.builder()
                        .fileName(invocation.getArgument(0))
                        .metadata(Map.of("sessionId", "fake-sessionId"))
                        .content(
                            TestUtil.getFileContent(
                                "xmlcontent/nodoInviaFlussoRendicontazione.xml"))
                        .build());

    // Mock table entities
    List<TableEntity> entities = new ArrayList<>();
    for (int i = 1; i <= 2; i++) {
      TableEntity entity =
          new TableEntity("testPartition", "row" + i)
              .addProperty("name", "EntityName" + i)
              .addProperty("value", i * 10)
              .addProperty("status", "active")
              .addProperty("fileName", "fake-file-name")
              .addProperty("fdr", "fake-fdr")
              .addProperty("pspId", "fake-pspId");
      entities.add(entity);
    }
    lenient()
        .when(mockTableClient.listEntities())
        .thenReturn(TestUtil.createPagedIterable(new IterableStream<>(entities)));

    lenient().when(mockTableClient.getEntity(anyString(), anyString())).thenReturn(entities.get(0));

    String responseBody =
        "{\"httpStatusCode\":404,\"httpStatusDescription\":\"Not Found\",\"appErrorCode\":\"FDR-3001\",\"errors\":[{\"message\":\"Flow with ID [mock-flow-id] not found.\"}]}";
    Map<String, List<String>> responseHeaders =
        new HashMap<>() {
          private static final long serialVersionUID = 1L;

          {
            put("content-type", Arrays.asList("application/json", "charset=UTF-8"));
          }
        };
    ApiException apiException = new ApiException(404, responseHeaders, responseBody);
    // Mock static methods of FdR3ClientUtil
    InternalPspApi mockInternalPspApi = mock(InternalPspApi.class);
    mockFdR3ClientUtil = mockStatic(FdR3ClientUtil.class);
    mockFdR3ClientUtil.when(FdR3ClientUtil::getPspApi).thenReturn(mockInternalPspApi);
    lenient()
        .when(mockInternalPspApi.internalDelete(anyString(), anyString()))
        .thenThrow(apiException);
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
  void testRun_withValidFilename() {
    HttpResponseMessage response = httpTriggerFn.run(request, "filename.xml.zip", context);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatus());
  }

  @Test
  void testRun_withoutFilename() {
    HttpResponseMessage response = httpTriggerFn.run(request, "", context);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
  }
}
