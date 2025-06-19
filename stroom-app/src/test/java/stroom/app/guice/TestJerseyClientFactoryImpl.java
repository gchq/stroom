package stroom.app.guice;

import stroom.config.app.Config;
import stroom.util.io.SimplePathCreator;
import stroom.util.jersey.JerseyClientName;
import stroom.util.shared.BuildInfo;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.client.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestJerseyClientFactoryImpl {

    /**
     * Mostly to make sure the factory ctor works ok
     */
    @Test
    void test(@TempDir final Path tempDir) {

        final Config config = new Config();
        final JerseyClientConfiguration jerseyClientConfiguration = new JerseyClientConfiguration();
        assertThat(jerseyClientConfiguration.getTlsConfiguration())
                .isNull();
        config.setJerseyClients(Map.of(
                JerseyClientName.DEFAULT.name(), jerseyClientConfiguration));

        final JerseyClientFactoryImpl proxyJerseyClientFactory = new JerseyClientFactoryImpl(
                config,
                () -> new BuildInfo(0L, "1.2.3", 0L),
                new Environment("test"),
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final Client defaultClient = proxyJerseyClientFactory.getDefaultClient();

        assertThat(defaultClient)
                .isNotNull();

        for (final JerseyClientName jerseyClientName : JerseyClientName.values()) {
            final Client namedClient = proxyJerseyClientFactory.getNamedClient(jerseyClientName);

            assertThat(namedClient)
                    .isNotNull();
        }
    }
}
