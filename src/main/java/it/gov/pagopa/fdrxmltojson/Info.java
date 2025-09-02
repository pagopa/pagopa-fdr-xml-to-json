package it.gov.pagopa.fdrxmltojson;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdrxmltojson.model.AppInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;


/**
 * Azure Functions with Azure Http trigger.
 */
@Slf4j
public class Info {

	private static final String ENVIRONMENT =
			System.getenv().getOrDefault("APP_ENVIRONMENT", "azure-fn");

	@FunctionName("Info")
	public HttpResponseMessage run (
			@HttpTrigger(name = "InfoTrigger",
					methods = {HttpMethod.GET},
					route = "info",
					authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		return request
				.createResponseBuilder(HttpStatus.OK)
				.header("Content-Type", "application/json")
				.body(getInfo())
				.build();
	}

	public synchronized AppInfo getInfo() {
		String version = null;
		String name = null;
		try (InputStream inputStream =
					 this.getClass().getClassLoader().getResourceAsStream("application.properties")) {
			Properties properties = new Properties();
			if (inputStream != null) {
				properties.load(inputStream);
				version = properties.getProperty("version", null);
				name = properties.getProperty("name", null);
			}
		} catch (Exception e) {
			log.error("Impossible to retrieve information from pom.properties file.", e);
		}
		return AppInfo.builder().version(version).environment(ENVIRONMENT).name(name).build();
	}
}
