package it.gov.pagopa.fdrxmltojson.util;

import com.azure.data.tables.TableClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdrxmltojson.model.BlobData;
import lombok.experimental.UtilityClass;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.IterableStream;
import com.azure.data.tables.models.TableEntity;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@UtilityClass
public class TestUtil {
    public static void setupEnvironmentVariables(EnvironmentVariables environmentVariables) {

        // Simulate environment variables
        environmentVariables.set("STORAGE_ACCOUNT_CONN_STRING", "UseDevelopmentStorage=true");
        environmentVariables.set("FDR1_FLOW_BLOB_CONTAINER_NAME", "fake-fdr1-flow");
        environmentVariables.set("ERROR_TABLE_NAME", "fake-fdr1-error");
        environmentVariables.set("ADD_PAYMENT_REQUEST_PARTITION_SIZE", 1000);
        environmentVariables.set("FDR_NEW_BASE_URL", "fdr3-internal-url");
        environmentVariables.set("FDR_NEW_API_KEY", "fdr3-internal-subscription-key");
    }

    public static void resetEnvironmentVariables(EnvironmentVariables environmentVariables) {
        // Reset to default or empty values
        environmentVariables.set("STORAGE_ACCOUNT_CONN_STRING", null);
        environmentVariables.set("FDR1_FLOW_BLOB_CONTAINER_NAME", null);
        environmentVariables.set("ERROR_TABLE_NAME", null);
        environmentVariables.set("ADD_PAYMENT_REQUEST_PARTITION_SIZE", null);
        environmentVariables.set("FDR_NEW_BASE_URL", null);
        environmentVariables.set("FDR_NEW_API_KEY", null);
    }

    public String readStringFromFile(String relativePath) throws IOException {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(relativePath)).getPath());
        return Files.readString(file.toPath());
    }

    public byte[] getFileContent(String fileName) throws IOException {
        String xml = TestUtil.readStringFromFile(fileName);
        return TestUtil.gzipCompress(xml.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] gzipCompress(byte[] uncompressedData) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
        GZIPOutputStream gzipOS = new GZIPOutputStream(bos);
        gzipOS.write(uncompressedData);
        gzipOS.close();
        return bos.toByteArray();
    }

    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("elaborate", "true");
        metadata.put("sessionId", UUID.randomUUID().toString());
        metadata.put("insertedTimestamp", LocalDate.now().toString());
        metadata.put("serviceIdentifier", "FDR001-UNIT-TEST");
        return metadata;
    }

    public static PagedIterable<TableEntity> createPagedIterable(IterableStream<TableEntity> entities) {
        PagedResponse<TableEntity> pagedResponse = new PagedResponse<TableEntity>() {

            @Override
            public String getContinuationToken() {
                return null;
            }

            @Override
            public int getStatusCode() {
                return 200;
            }

            @Override
            public IterableStream<TableEntity> getElements() {
                return entities;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public HttpRequest getRequest() {
                return null;
            }

            @Override
            public void close() throws IOException {
                // No-op
            }
        };

        PagedFlux<TableEntity> pagedFlux = new PagedFlux<>(continuationToken -> Mono.just(pagedResponse));
        return new PagedIterable<>(pagedFlux);
    }

}
