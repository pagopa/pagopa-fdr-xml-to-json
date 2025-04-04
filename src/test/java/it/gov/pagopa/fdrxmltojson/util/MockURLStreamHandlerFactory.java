package it.gov.pagopa.fdrxmltojson.util;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class MockURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private MockHttpURLConnection mockHttpURLConnection;

    public MockURLStreamHandlerFactory(MockHttpURLConnection mockHttpURLConnection) {
        this.mockHttpURLConnection = mockHttpURLConnection;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return new MockURLStreamHandler(this.mockHttpURLConnection);
    }
}
