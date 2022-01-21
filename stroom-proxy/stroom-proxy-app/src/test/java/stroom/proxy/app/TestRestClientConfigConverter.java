package stroom.proxy.app;

import stroom.util.io.DirProvidersModule;
import stroom.util.io.PathConfig;
import stroom.util.io.PathCreator;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import io.dropwizard.client.JerseyClientConfiguration;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;

@ExtendWith(GuiceExtension.class)
@IncludeModule(DirProvidersModule.class)
@IncludeModule(TestRestClientConfigConverter.TestModule.class)
class TestRestClientConfigConverter {

    @Inject
    PathCreator pathCreator;

    @Test
    void test() {
        final RestClientConfigConverter restClientConfigConverter = new RestClientConfigConverter(pathCreator);

        final RestClientConfig restClientConfig = RestClientConfig
                .builder()
                .withUserAgent("foobar")
                .withMaxThreads(999)
                .withConnectionTimeout(StroomDuration.ofMinutes(33))
                .withTlsConfiguration(HttpClientTlsConfig.builder()
                        .withCertAlias("bongo")
                        .build())
                .build();

        final JerseyClientConfiguration jerseyClientConfiguration = restClientConfigConverter.convert(
                restClientConfig);

        Assertions.assertThat(jerseyClientConfiguration.getMaxThreads())
                .isEqualTo(restClientConfig.getMaxThreads());

        Assertions.assertThat(jerseyClientConfiguration.getConnectionTimeout().toMilliseconds())
                .isEqualTo(restClientConfig.getConnectionTimeout().toMillis());

        Assertions.assertThat(jerseyClientConfiguration.getTlsConfiguration().getCertAlias())
                .isEqualTo(restClientConfig.getTlsConfiguration().getCertAlias());

    }

    static class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(PathConfig.class).toInstance(new ProxyPathConfig());
        }
    }

}
