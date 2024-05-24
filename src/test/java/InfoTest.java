import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdrxmltojson.Info;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InfoTest {

    @Mock
    ExecutionContext context;

    @Spy
    Info infoFunction;

    final HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
    final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);

    @SneakyThrows
    @Test
    void getInfoOk() {
        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
        doReturn(HttpStatus.OK).when(responseMock).getStatus();
        doReturn(responseMock).when(builder).build();
        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = infoFunction.run(request, context);

        assertEquals(200, response.getStatus().value());
    }

    @SneakyThrows
    @Test
    void getInfoKo() {
        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
        doReturn(HttpStatus.INTERNAL_SERVER_ERROR).when(responseMock).getStatus();
        doReturn(responseMock).when(builder).build();
        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage response = infoFunction.run(request, context);

        assertEquals(500, response.getStatus().value());
    }
}
