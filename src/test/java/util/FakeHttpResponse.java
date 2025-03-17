package util;

import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;

/**
 * Classe fittizia per simulare HttpResponseMessage nei test.
 */
public class FakeHttpResponse implements HttpResponseMessage {

    private final HttpStatusType status;
    private final Object body;
    private final Map<String, String> headers;

    public FakeHttpResponse(HttpStatusType status, Map<String, String> headers, Object body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    @Override
    public HttpStatusType getStatus() {
        return status;
    }

    @Override
    public String getHeader(String key) {
        return headers.get(key);
    }

    public Map<String, String> getHeaders() { 
        return headers;
    }

    @Override
    public Object getBody() {
        return body;
    }
}
