package stroom.proxy.app.guice;

import stroom.proxy.app.RestClientConfig;
import stroom.proxy.app.RestClientConfigConverter;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.logging.LoggingFeature;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;

@Singleton // This class is a singleton as it is stateful
public class ProxyJerseyClientProvider implements Provider<Client> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyJerseyClientProvider.class);

    // This name is used by dropwizard metrics
    private static final String PROXY_JERSEY_CLIENT_NAME = "stroom-proxy_jersey_client";
    private static final String PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom-proxy/";

    private volatile Client client = null;
    private volatile RestClientConfig restClientConfig = null;

    private final Provider<RestClientConfig> restClientConfigProvider;
    private final Environment environment;
    private final Provider<BuildInfo> buildInfoProvider;
    private final PathCreator pathCreator;
    private final RestClientConfigConverter restClientConfigConverter;

    @Inject
    public ProxyJerseyClientProvider(final Provider<RestClientConfig> restClientConfigProvider,
                                     final Environment environment,
                                     final Provider<BuildInfo> buildInfoProvider,
                                     final PathCreator pathCreator,
                                     final RestClientConfigConverter restClientConfigConverter) {
        this.restClientConfigProvider = restClientConfigProvider;
        this.environment = environment;
        this.buildInfoProvider = buildInfoProvider;
        this.pathCreator = pathCreator;
        this.restClientConfigConverter = restClientConfigConverter;
    }

    private boolean isNewClientRequired(final RestClientConfig currentRestClientConfig) {
        // Config objects are immutable so comparison by instance is fine
        return client == null
                || restClientConfig == null
                || restClientConfig != currentRestClientConfig;
    }

    @Override
    public Client get() {
        RestClientConfig currentRestClientConfig = restClientConfigProvider.get();

        if (isNewClientRequired(currentRestClientConfig)) {
            synchronized (this) {
                // Re-fetch and test config now we are under lock
                currentRestClientConfig = restClientConfigProvider.get();
                if (isNewClientRequired(currentRestClientConfig)) {
                    try {
                        this.client = createClient(currentRestClientConfig);
                        this.restClientConfig = currentRestClientConfig;
                    } catch (Exception e) {
                        if (this.client == null) {
                            // Now existing client to fall back on
                            throw new RuntimeException(LogUtil.message(
                                    "Error creating jersey client, msg: {}, config: {}",
                                    e.getMessage(), currentRestClientConfig), e);
                        } else {
                            LOGGER.error("Error creating jersey client, msg: {}, config: {} " +
                                            "Proxy will continue to use previous client based on previous config: {}",
                                    e.getMessage(), currentRestClientConfig, this.restClientConfig);
                        }
                    }
                }
            }
        }
        return Objects.requireNonNull(client);
    }

    private Client createClient(final RestClientConfig restClientConfig) {
        LOGGER.info("Creating Jersey client");
        // RestClientConfig is really just a copy of JerseyClientConfiguration
        // so do the conversion
        final JerseyClientConfiguration jerseyClientConfiguration = restClientConfigConverter.convert(
                restClientConfig);

        // If the userAgent has not been explicitly set in the config then set it based
        // on the build version
        if (jerseyClientConfiguration.getUserAgent().isEmpty()) {
            final String userAgent = PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX
                    + buildInfoProvider.get().getBuildVersion();
            LOGGER.info("Setting rest client user agent string to [{}]", userAgent);
            jerseyClientConfiguration.setUserAgent(Optional.of(userAgent));
        }

        // Mutating the TLS config is not ideal, but I'm not sure if there is another way.
        // We need to allow for relative paths (relative to proxy home), '~', and other system
        // props in the path. Therefore, if path creator produces a different path to what was
        // configured then update the config object.
        final TlsConfiguration tlsConfiguration = jerseyClientConfiguration.getTlsConfiguration();
        if (tlsConfiguration != null) {
            if (tlsConfiguration.getKeyStorePath() != null) {
                final File modifiedKeyStorePath = pathCreator.toAppPath(tlsConfiguration.getKeyStorePath().getPath())
                        .toFile();

                if (!modifiedKeyStorePath.getPath().equals(tlsConfiguration.getKeyStorePath().getPath())) {
                    LOGGER.info("Updating rest client key store path from {} to {}",
                            tlsConfiguration.getKeyStorePath(),
                            modifiedKeyStorePath);
                    tlsConfiguration.setKeyStorePath(modifiedKeyStorePath);
                }
            }

            if (tlsConfiguration.getTrustStorePath() != null) {
                final File modifiedTrustStorePath = pathCreator.toAppPath(
                        tlsConfiguration.getTrustStorePath().getPath()).toFile();

                if (!modifiedTrustStorePath.getPath().equals(tlsConfiguration.getTrustStorePath().getPath())) {
                    LOGGER.info("Updating rest client trust store path from {} to {}",
                            tlsConfiguration.getTrustStorePath(),
                            modifiedTrustStorePath);
                    tlsConfiguration.setTrustStorePath(modifiedTrustStorePath);
                }
            }
        }

        LOGGER.info("Creating jersey rest client {}", PROXY_JERSEY_CLIENT_NAME);
        return new JerseyClientBuilder(environment)
                .using(jerseyClientConfiguration)
                .build(PROXY_JERSEY_CLIENT_NAME)
                .register(LoggingFeature.class);
    }
}
