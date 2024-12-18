package stroom.util.http;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public interface HttpClientFactory {

    CloseableHttpClient get(String name, HttpClientConfiguration httpClientConfiguration);
}
