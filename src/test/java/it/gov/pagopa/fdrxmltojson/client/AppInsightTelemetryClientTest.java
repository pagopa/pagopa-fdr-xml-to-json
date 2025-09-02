package it.gov.pagopa.fdrxmltojson.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class AppInsightTelemetryClientTest {

  @Mock private TelemetryClient telemetryClientMock;

  @InjectMocks private AppInsightTelemetryClient sut;

  @Test
  void createCustomEventForAlertWithSuccess() {
    assertDoesNotThrow(() -> sut.createCustomEventForAlert("error detail", new Exception("test")));

    verify(telemetryClientMock).trackEvent(eq("FDR_XML_TO_JSON_ALERT"), anyMap(), eq(null));
  }
}
