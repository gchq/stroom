package stroom.proxy.app;

import stroom.util.time.StroomDuration;

import io.dropwizard.client.JerseyClientConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRestClientConfigConverter {

    @Test
    void test() {
        final RestClientConfigConverter restClientConfigConverter = new RestClientConfigConverter();

        final RestClientConfig restClientConfig = new RestClientConfig();

        restClientConfig.setMaxThreads(999);
        restClientConfig.setConnectionTimeout(StroomDuration.ofMinutes(33));

        final HttpClientTlsConfig httpClientTlsConfig = new HttpClientTlsConfig();
        httpClientTlsConfig.setCertAlias("bongo");

        restClientConfig.setTlsConfiguration(httpClientTlsConfig);

        final JerseyClientConfiguration jerseyClientConfiguration = restClientConfigConverter.convert(
                restClientConfig);

        Assertions.assertThat(jerseyClientConfiguration.getMaxThreads())
                .isEqualTo(restClientConfig.getMaxThreads());

        Assertions.assertThat(jerseyClientConfiguration.getConnectionTimeout().toMilliseconds())
                .isEqualTo(restClientConfig.getConnectionTimeout().toMillis());

        Assertions.assertThat(jerseyClientConfiguration.getTlsConfiguration().getCertAlias())
                .isEqualTo(restClientConfig.getTlsConfiguration().getCertAlias());

    }


}
