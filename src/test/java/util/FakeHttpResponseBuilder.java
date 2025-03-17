package util;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;

public class FakeHttpResponseBuilder implements HttpResponseMessage.Builder {
    private HttpStatusType status;
    private Object body;
    private final Map<String, String> headers = new HashMap<>();

    @Override
    public HttpResponseMessage.Builder status(HttpStatusType status) {
        this.status = status;
        return this;
    }

    @Override
    public HttpResponseMessage.Builder header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    @Override
    public HttpResponseMessage.Builder body(Object body) {
        this.body = body;
        return this;
    }

    @Override
    public HttpResponseMessage build() {
        return new FakeHttpResponse(status, headers, body);
    }
}

