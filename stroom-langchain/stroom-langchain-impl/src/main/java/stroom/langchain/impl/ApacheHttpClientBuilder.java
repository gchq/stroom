package stroom.langchain.impl;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;

public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private final HttpClientProviderCache httpClientProviderCache;
    private HttpClientConfiguration httpClientConfiguration;

    public ApacheHttpClientBuilder(final HttpClientProviderCache httpClientProviderCache,
                                   final HttpClientConfiguration httpClientConfiguration) {
        this.httpClientProviderCache = httpClientProviderCache;
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
        httpClientConfiguration = httpClientConfiguration.copy().connectionTimeout(StroomDuration.of(timeout)).build();
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
        httpClientConfiguration = httpClientConfiguration.copy().timeout(StroomDuration.of(timeout)).build();
        return this;
    }

    @Override
    public HttpClient build() {
        return new ApacheHttpClient(httpClientProviderCache, httpClientConfiguration);
    }
}
