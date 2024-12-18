package stroom.util.http;

import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public class BasicHttpClientFactory implements HttpClientFactory {

    private final HttpClientConfigConverter httpClientConfigConverter;

    @Inject
    public BasicHttpClientFactory(final HttpClientConfigConverter httpClientConfigConverter) {
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
        return new HttpClientBuilder((Environment) null)
                .using(configuration)
                .build(name);
    }
}
