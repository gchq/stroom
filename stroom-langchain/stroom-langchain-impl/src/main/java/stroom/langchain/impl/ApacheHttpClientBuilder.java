package stroom.langchain.impl;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private final org.apache.hc.client5.http.classic.HttpClient httpClient;

    public ApacheHttpClientBuilder(final org.apache.hc.client5.http.classic.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Duration connectTimeout() {
        return null;
    }

    @Override
    public HttpClientBuilder connectTimeout(final Duration timeout) {
        return null;
    }

    @Override
    public Duration readTimeout() {
        return null;
    }

    @Override
    public HttpClientBuilder readTimeout(final Duration timeout) {
        return null;
    }

    @Override
    public HttpClient build() {
        return new ApacheHttpClient(httpClient);
    }
}
