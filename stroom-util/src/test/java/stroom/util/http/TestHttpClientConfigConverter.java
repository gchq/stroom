package stroom.util.http;

import stroom.test.common.TemporaryPathCreator;
import stroom.util.config.SampleObjectCreator;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHttpClientConfigConverter {

    @Test
    void test() {
        final PathCreator pathCreator = new TemporaryPathCreator();
        final HttpClientConfigConverter restClientConfigConverter = new HttpClientConfigConverter(pathCreator);

        HttpClientConfiguration httpClientConfiguration =
                SampleObjectCreator.createPopulatedObject(HttpClientConfiguration.class, null);
        httpClientConfiguration = httpClientConfiguration
                .copy()
                .tlsConfiguration(httpClientConfiguration
                        .getTlsConfiguration()
                        .copy()
                        .keyStorePath("/keystorepath")
                        .trustStorePath("/trustStorepath")
                        .build())
                .build();

        final io.dropwizard.client.HttpClientConfiguration out = restClientConfigConverter.convert(
                httpClientConfiguration,
                io.dropwizard.client.HttpClientConfiguration.class);

        final HttpClientConfiguration reverse = restClientConfigConverter.convert(out,
                stroom.util.http.HttpClientConfiguration.class);

        assertThat(httpClientConfiguration).isEqualTo(reverse);
    }
}
