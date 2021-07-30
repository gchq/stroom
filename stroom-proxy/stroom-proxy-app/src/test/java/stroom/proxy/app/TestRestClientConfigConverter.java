package stroom.proxy.app;

import io.dropwizard.client.JerseyClientConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRestClientConfigConverter {

    @Test
    void test() {
        final RestClientConfigConverter restClientConfigConverter = new RestClientConfigConverter();

        final RestClientConfig restClientConfig = new RestClientConfig();
        restClientConfig.setMaxThreads(999);
        final HttpClientTlsConfig httpClientTlsConfig = new HttpClientTlsConfig();
        httpClientTlsConfig.setCertAlias("bongo");
        restClientConfig.setTlsConfiguration(new HttpClientTlsConfig());

        final JerseyClientConfiguration jerseyClientConfiguration = restClientConfigConverter.convert(
                new RestClientConfig());

        Assertions.assertThat(jerseyClientConfiguration.getMaxThreads())
                .isEqualTo(restClientConfig.getMaxThreads());
        Assertions.assertThat(jerseyClientConfiguration.getTlsConfiguration().getCertAlias())
                .isEqualTo(restClientConfig.getTlsConfiguration().getCertAlias());

    }




}