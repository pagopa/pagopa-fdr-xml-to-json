package util;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.IterableStream;
import com.azure.data.tables.models.TableEntity;

@UtilityClass
public class TestUtil {

    public String readStringFromFile(String relativePath) throws IOException {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource(relativePath)).getPath());
        return Files.readString(file.toPath());
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
