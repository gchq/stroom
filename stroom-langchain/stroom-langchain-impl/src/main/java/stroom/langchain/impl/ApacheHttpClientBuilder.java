package stroom.langchain.impl;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.shared.NullSafe;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private final org.apache.hc.client5.http.classic.HttpClient httpClient;
    private final HttpClientConfiguration httpClientConfiguration;

    public ApacheHttpClientBuilder(final org.apache.hc.client5.http.classic.HttpClient httpClient,
                                   final HttpClientConfiguration httpClientConfiguration) {
        this.httpClient = httpClient;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public Duration connectTimeout() {
        return NullSafe.getOrElse(httpClientConfiguration,
                        HttpClientConfiguration::getConnectionTimeout,
                        HttpClientConfiguration.DEFAULT_CONNECTION_TIMEOUT)
                .getDuration();
    }

    @Override
    public HttpClientBuilder connectTimeout(final Duration timeout) {
        return this;
    }

    @Override
    public Duration readTimeout() {
        return NullSafe.getOrElse(httpClientConfiguration,
                        HttpClientConfiguration::getTimeout,
                        HttpClientConfiguration.DEFAULT_TIMEOUT)
                .getDuration();
    }

    @Override
    public HttpClientBuilder readTimeout(final Duration timeout) {
        return this;
    }

    @Override
    public HttpClient build() {
        return new ApacheHttpClient(httpClient);
    }
}
