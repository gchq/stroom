package stroom.dropwizard.common;

import stroom.util.http.HttpClientConfigConverter;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;

import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public class HttpClientFactoryImpl implements HttpClientFactory {

    private final Environment environment;
    private final HttpClientConfigConverter httpClientConfigConverter;

    @Inject
    public HttpClientFactoryImpl(final Environment environment,
                                 final HttpClientConfigConverter httpClientConfigConverter) {
        this.environment = environment;
        this.httpClientConfigConverter = httpClientConfigConverter;
    }

    @Override
    public CloseableHttpClient get(final String name,
                                   final HttpClientConfiguration httpClientConfiguration) {
        // Now create a new client.
        io.dropwizard.client.HttpClientConfiguration configuration = httpClientConfigConverter.convert(
                httpClientConfiguration,
                io.dropwizard.client.HttpClientConfiguration.class);
        if (configuration == null) {
            configuration = new io.dropwizard.client.HttpClientConfiguration();
        }
        return new HttpClientBuilder(environment)
                .using(configuration)
                .build(name);
    }
}
