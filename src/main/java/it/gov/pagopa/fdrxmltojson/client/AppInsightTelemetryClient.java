package it.gov.pagopa.fdrxmltojson.client;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import java.util.Map;

/** Azure Application Insight Telemetry client */
public class AppInsightTelemetryClient {

  private final String connectionString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");

  private final TelemetryClient telemetryClient;

  public AppInsightTelemetryClient() {
    TelemetryConfiguration aDefault = TelemetryConfiguration.createDefault();
    aDefault.setConnectionString(connectionString);
    this.telemetryClient = new TelemetryClient(aDefault);
  }

  AppInsightTelemetryClient(TelemetryClient telemetryClient) {
    this.telemetryClient = telemetryClient;
  }

  /**
   * Create a custom event on Application Insight with the provided information
   *
   * @param details details of the custom event
   * @param e exception added to the custom event
   */
  public void createCustomEventForAlert(String details, Exception e) {
    Map<String, String> props =
        Map.of(
            "type",
            "FDR_XML_TO_JSON_ERROR",
            "title",
            "FdrXmlToJson last retry",
            "details",
            details,
            "cause",
            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
    this.telemetryClient.trackEvent("FDR_XML_TO_JSON_ALERT", props, null);
  }
}
