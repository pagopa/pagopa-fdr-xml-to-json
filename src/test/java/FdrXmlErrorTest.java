import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.fdrxmltojson.FdrXmlError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import util.TestUtil;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @SneakyThrows
    void runOk_withoutAdditionalProperties() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);
        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);

        doReturn(HttpStatus.OK).when(responseMock).getStatus();
        doReturn(responseMock).when(builder).build();
        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        TableServiceClientBuilder tableServiceClientBuilder = mock(TableServiceClientBuilder.class);
        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        when(tableServiceClientBuilder.buildClient()).thenReturn(tableServiceClient);

        OutputBinding<List<Object>> document = (OutputBinding<List<Object>>) mock(OutputBinding.class);

        // generating input
        String eventInStringForm = "";//TestUtil.readStringFromFile("events/event_ok_1.json");
        List<String> events = new ArrayList<>();
        events.add(eventInStringForm);
        Map<String, Object>[] properties = new HashMap[1];
        properties[0] = new HashMap<>();

        // generating expected output
        Map<String, Object> expectedEvent = new ObjectMapper().readValue(eventInStringForm, Map.class);
        expectedEvent.put("PartitionKey", "20231212-77777777777-88888888888");
//        ((Map)expectedEvent.get(Constants.FAULTBEAN_EVENT_FIELD)).put(Constants.TIMESTAMP_EVENT_FIELD, 1702406079);
//        ((Map)expectedEvent.get(Constants.FAULTBEAN_EVENT_FIELD)).put(Constants.DATE_TIME_EVENT_FIELD, "2023-12-12T18:34:39.860654");
        List<Object> expectedEventsToPersist = List.of(expectedEvent);

        // execute logic
        fdrXmlError.run(request, context);

//        ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
//        verify(document).setValue(captor.capture());
//        List<Object> actualEventsToPersist = captor.getValue();
//        assertEquals(convertWithStream(expectedEventsToPersist), convertWithStream(actualEventsToPersist));
    }

}
