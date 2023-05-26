package stroom.dropwizard.common;

import stroom.util.NullSafe;
import stroom.util.io.PathCreator;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.DefaultLoggingFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Environment;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

public abstract class AbstractJerseyClientFactory implements JerseyClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractJerseyClientFactory.class);
    // This name is used by dropwizard metrics

    private final Provider<BuildInfo> buildInfoProvider;
    private final Environment environment;
    private final PathCreator pathCreator;
    private final Map<JerseyClientName, JerseyClientConfiguration> configMap = new EnumMap<>(JerseyClientName.class);
    private final Map<JerseyClientName, Client> clientMap = new EnumMap<>(JerseyClientName.class);

    public AbstractJerseyClientFactory(final Provider<BuildInfo> buildInfoProvider,
                                       final Environment environment,
                                       final PathCreator pathCreator,
                                       final Map<String, JerseyClientConfiguration> configurationMap) {
        this.buildInfoProvider = buildInfoProvider;
        this.environment = environment;
        this.pathCreator = pathCreator;
        // Build a map of all jersey configs keyed on JerseyClientName. Will only contain names
        // found in the config file
        configurationMap
                .forEach((name, jerseyClientConfig) -> {
                    if (jerseyClientConfig != null) {
                        try {
                            final JerseyClientName jerseyClientName = JerseyClientName.valueOf(name.toUpperCase());

                            //
                            modifyConfig(jerseyClientConfig);

                            configMap.put(jerseyClientName, jerseyClientConfig);
                        } catch (IllegalArgumentException e) {
                            throw new RuntimeException(LogUtil.message(
                                    "Unknown jerseyClient name '{}' in configuration. Expecting one of {}",
                                    name.toUpperCase(), JerseyClientName.values()), e);
                        }
                    }
                });
    }

    /**
     * @return The prefix for the name used in dropwizard metrics
     */
    protected abstract String getJerseyClientNamePrefix();

    /**
     * @return The prefix for the user agent name
     */
    protected abstract String getJerseyClientUserAgentPrefix();

    @Override
    public Client getNamedClient(final JerseyClientName jerseyClientName) {
        Objects.requireNonNull(jerseyClientName);
        Client client = clientMap.get(jerseyClientName);
        // Lazily create clients, so we don't create ones that are not needed
        if (client == null) {
            LOGGER.debug("No client for {}", jerseyClientName);
            synchronized (this) {
                client = clientMap.computeIfAbsent(jerseyClientName, this::buildNamedClient);
            }
            if (client == null) {
                throw new RuntimeException(LogUtil.message("No client found for name {}", jerseyClientName));
            }
        }
        return client;
    }

    @Override
    public Client getDefaultClient() {
        return getNamedClient(JerseyClientName.DEFAULT);
    }

    @Override
    public WebTarget createWebTarget(final JerseyClientName jerseyClientName, final String endpoint) {
        Objects.requireNonNull(endpoint);
        final Client client = getNamedClient(jerseyClientName);
        return client.target(endpoint);
    }

    private Client buildNamedClient(final JerseyClientName jerseyClientName) {
        JerseyClientConfiguration jerseyClientConfiguration = configMap.get(jerseyClientName);
        LOGGER.debug("jerseyClientConfiguration for {}: {}",
                jerseyClientName, jerseyClientConfiguration);

        final JerseyClientName defaultClientName = JerseyClientName.DEFAULT;

        if (jerseyClientConfiguration == null) {
            if (defaultClientName.equals(jerseyClientName)) {

                jerseyClientConfiguration = configMap.get(defaultClientName);
                LOGGER.debug("jerseyClientConfiguration for {}: {}",
                        jerseyClientName, jerseyClientConfiguration);
            } else {
                // No config for this name, so recurse to see if we have a client for DEFAULT or create one
                // if we don't
                final Client defaultClient = getNamedClient(defaultClientName);
                Objects.requireNonNull(defaultClient);
                LOGGER.info("Using jersey client '{}' for name '{}'", defaultClientName, jerseyClientName);
                // The existing client will be in the map multiple times
                return defaultClient;
            }
        }

        if (jerseyClientConfiguration == null) {
            LOGGER.debug("Creating new vanilla JerseyClientConfiguration for name {}",
                    jerseyClientName);
            jerseyClientConfiguration = new JerseyClientConfiguration();
        }
        // If jerseyClientConfiguration is null here it will create a vanilla one
        return buildClient(jerseyClientName, jerseyClientConfiguration);
    }

    private Client buildClient(final JerseyClientName jerseyClientName,
                               final JerseyClientConfiguration jerseyClientConfiguration) {
        Objects.requireNonNull(jerseyClientName);
        Objects.requireNonNull(jerseyClientConfiguration);
        final String dropWizardName = getJerseyClientNamePrefix().toLowerCase()
                + jerseyClientName.name().toLowerCase();

        final String userAgent = jerseyClientConfiguration.getUserAgent()
                .orElse(null);

        LOGGER.info("Building and registering jersey client for name '{}', DropWizard metric name '{}', userAgent '{}'",
                jerseyClientName, dropWizardName, userAgent);
        try {
            return new JerseyClientBuilder(environment)
                    .using(jerseyClientConfiguration)
                    .build(dropWizardName)
                    .register(DefaultLoggingFilter.createWithDefaults());
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error building jersey client for '{}': {}",
                    jerseyClientName, e.getMessage()), e);
        }
    }

    private void modifyConfig(final JerseyClientConfiguration jerseyClientConfiguration) {
        // If the userAgent has not been explicitly set in the config then set it based
        // on the build version
        jerseyClientConfiguration.getUserAgent()
                .filter(str -> !str.isBlank())
                .orElseGet(() -> {
                    final String userAgent2 = getJerseyClientUserAgentPrefix().toLowerCase()
                            + buildInfoProvider.get().getBuildVersion();
                    LOGGER.debug("Setting jersey client user agent string to [{}]", userAgent2);
                    jerseyClientConfiguration.setUserAgent(Optional.of(userAgent2));
                    return userAgent2;
                });

        NullSafe.consume(
                jerseyClientConfiguration,
                JerseyClientConfiguration::getTlsConfiguration,
                tlsConfiguration -> {
                    modifyPath(tlsConfiguration::getKeyStorePath, tlsConfiguration::setKeyStorePath);
                    modifyPath(tlsConfiguration::getTrustStorePath, tlsConfiguration::setTrustStorePath);
                });
    }

    private void modifyPath(final Supplier<File> getter,
                            final Consumer<File> setter) {
        Objects.requireNonNull(getter);
        Objects.requireNonNull(setter);

        // Need to support relative paths in the same way that we do for our own config
        final File file = getter.get();
        if (file != null) {
            final Path newPath = pathCreator.toAppPath(file.getPath());
            if (!Files.exists(newPath)) {
                throw new RuntimeException(LogUtil.message(
                        "File '{}' in jersey client configuration '{}' does not exist", newPath));
            }
            final File newFile = newPath.toFile();
            LOGGER.debug("Changing {} to {}", file, newFile);
            setter.accept(newFile);
        }
    }
}
