/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dropwizard.common;

import stroom.util.config.PropertyUtil;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.io.PathCreator;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.DefaultLoggingFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.proxy.ProxyConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.util.Duration;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class AbstractJerseyClientFactory implements JerseyClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractJerseyClientFactory.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.seconds(0);
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.seconds(30);
    private static final String TLS_PROP_NAME = "tls";
    private static final String PROXY_PROP_NAME = "proxy";

    private static final Map<JerseyClientName, JerseyClientConfiguration> CONFIG_DEFAULTS_MAP = new EnumMap<>(
            JerseyClientName.class);

    static {
        CONFIG_DEFAULTS_MAP.put(JerseyClientName.OPEN_ID, buildOpenIdClientDefaultConfig());
        CONFIG_DEFAULTS_MAP.put(JerseyClientName.DEFAULT, buildDefaultClientDefaultConfig());
        CONFIG_DEFAULTS_MAP.put(JerseyClientName.STROOM, buildStroomClientDefaultConfig());
    }

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
        // Build a map of all jersey configs found in the config.yml, keyed on JerseyClientName.
        // Will only contain names found in the config file. For each one merge in our hard coded default
        // values.
        configurationMap
                .forEach((name, jerseyClientConfig) -> {
                    if (jerseyClientConfig != null) {
                        try {
                            final JerseyClientName jerseyClientName = JerseyClientName.valueOf(name.toUpperCase());

                            // Merge in any hard coded defaults
                            modifyConfig(jerseyClientName, jerseyClientConfig);

                            configMap.put(jerseyClientName, jerseyClientConfig);
                        } catch (final IllegalArgumentException e) {
                            throw new RuntimeException(LogUtil.message(
                                    "Unknown jerseyClient name '{}' in configuration. Expecting one of {}",
                                    name.toUpperCase(), JerseyClientName.values()), e);
                        }
                    }
                });

        final boolean isDefaultExplicitlyConfigured = configMap.containsKey(JerseyClientName.DEFAULT);

        // Make sure we have our hard coded config defaults loaded
        Arrays.stream(JerseyClientName.values())
                .forEach(jerseyClientName -> {
                    if (!configMap.containsKey(jerseyClientName)
                        && CONFIG_DEFAULTS_MAP.containsKey(jerseyClientName)) {

                        final JerseyClientConfiguration defaultConfig = CONFIG_DEFAULTS_MAP.get(
                                jerseyClientName);

                        LOGGER.debug("Creating config with name: {}", jerseyClientName);

                        // DEFAULT has been set in config so merge its values into the named client config
                        // that was not set in config.yml
                        if (!JerseyClientName.DEFAULT.equals(jerseyClientName) && isDefaultExplicitlyConfigured) {
                            LOGGER.debug("Merging values from DEFAULT into {}", jerseyClientName);
                            final JerseyClientConfiguration mergedConfig = mergeInStroomDefaults(
                                    buildDefaultClientDefaultConfig(),
                                    configMap.get(JerseyClientName.DEFAULT),
                                    defaultConfig);
                            configMap.put(jerseyClientName, mergedConfig);
                        } else {
                            LOGGER.debug("Config for {} not set in yaml so using hard coded defaults",
                                    jerseyClientName);
                            configMap.put(jerseyClientName, CONFIG_DEFAULTS_MAP.get(jerseyClientName));
                        }
                    }
                });

        if (LOGGER.isDebugEnabled()) {
            logNonDefaultJerseyClientConfig(LOGGER::debug);
        }
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
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error building jersey client for '{}': {}",
                    jerseyClientName, e.getMessage()), e);
        }
    }

    private void modifyConfig(final JerseyClientName jerseyClientName,
                              final JerseyClientConfiguration jerseyClientConfiguration) {

        mergeInStroomDefaults(
                new JerseyClientConfiguration(),
                CONFIG_DEFAULTS_MAP.get(jerseyClientName),
                jerseyClientConfiguration);

        updateUserAgent(jerseyClientConfiguration);

        NullSafe.consume(
                jerseyClientConfiguration,
                JerseyClientConfiguration::getTlsConfiguration,
                tlsConfiguration -> {
                    modifyPath(tlsConfiguration::getKeyStorePath, tlsConfiguration::setKeyStorePath);
                    modifyPath(tlsConfiguration::getTrustStorePath, tlsConfiguration::setTrustStorePath);
                });
    }

    // Pkg private for testing
    static <T> T mergeInStroomDefaults(final T vanillaConfig,
                                       final T stroomDefaultConfig,
                                       final T actualConfig) {
        return mergeInStroomDefaults("", vanillaConfig, stroomDefaultConfig, actualConfig);
    }

    // Pkg private for testing
    static <T> T mergeInStroomDefaults(final String prefix,
                                       final T vanillaConfig,
                                       final T stroomDefaultConfig,
                                       final T actualConfig) {
        if (stroomDefaultConfig != null) {
            final Map<String, Prop> props = PropertyUtil.getProperties(vanillaConfig);

            props.forEach((propName, prop) -> {
                // Value from the dropwiz class as is
                final Object vanillaValue = getPropValue(prop, vanillaConfig);
                // Our hard coded sensible default (may be same as above)
                final Object stroomDefaultValue = getPropValue(prop, stroomDefaultConfig);
                // Value from yaml
                final Object actualValue = getPropValue(prop, actualConfig);
                LOGGER.debug("Prop: {}.{}\nVanilla: {}\nStroom Default: {}\nActual: {}",
                        prefix, propName, vanillaValue, stroomDefaultValue, actualValue);

                if (propName.equals(TLS_PROP_NAME)
                    && (stroomDefaultValue != null || actualValue != null)) {
                    if (vanillaValue != null) {
                        throw new RuntimeException("Expecting vanilla config to be null. " +
                                                   "Have DropWizard changed their default config");
                    }
                    final Object mergedValue;
                    if (stroomDefaultValue != null && actualValue == null) {
                        mergedValue = stroomDefaultValue;
                    } else {
                        // tls config is null in vanilla config
                        mergedValue = mergeInStroomDefaults(
                                TLS_PROP_NAME,
                                new TlsConfiguration(),
                                stroomDefaultValue,
                                actualValue);
                    }
                    prop.setValueOnConfigObject(actualConfig, mergedValue);
                } else if (propName.equals(PROXY_PROP_NAME)
                           && (stroomDefaultValue != null || actualValue != null)) {
                    if (vanillaValue != null) {
                        throw new RuntimeException("Expecting vanilla config to be null. " +
                                                   "Have DropWizard changed their default config");
                    }
                    final Object mergedValue;
                    if (stroomDefaultValue != null && actualValue == null) {
                        mergedValue = stroomDefaultValue;
                    } else {
                        // proxy (as in a proxy server, not stroom-proxy) config is null in vanilla config
                        mergedValue = mergeInStroomDefaults(PROXY_PROP_NAME,
                                new ProxyConfiguration(),
                                stroomDefaultValue,
                                actualValue);
                    }
                    prop.setValueOnConfigObject(actualConfig, mergedValue);
                } else {
                    if (Objects.equals(vanillaValue, actualValue)
                        && !Objects.equals(vanillaValue, stroomDefaultValue)) {
                        // yaml value is same as DW default but stroom has a different default
                        // so apply that
                        LOGGER.debug(() ->
                                LogUtil.message("Changing property {} from {} to stroom default {}",
                                        prefix + propName, vanillaValue, stroomDefaultValue));
                        prop.setValueOnConfigObject(actualConfig, stroomDefaultValue);
                    }
                }
            });
        }
        return actualConfig;
    }

    private static Object getPropValue(final Prop prop, final Object obj) {
        return NullSafe.get(obj, prop::getValueFromConfigObject);
    }

    private void updateUserAgent(final JerseyClientConfiguration jerseyClientConfiguration) {
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

    /**
     * The config for comms with an OIDC identity provider
     */
    static JerseyClientConfiguration buildOpenIdClientDefaultConfig() {
        final JerseyClientConfiguration jerseyConfig = new JerseyClientConfiguration();
        // Various OIDC providers don't like gzip encoded requests so turn it off
        jerseyConfig.setGzipEnabledForRequests(false);
        setCommonValues(jerseyConfig);
        return jerseyConfig;
    }

    /**
     * The config for stroom internode comms
     */
    static JerseyClientConfiguration buildStroomClientDefaultConfig() {
        final JerseyClientConfiguration jerseyConfig = new JerseyClientConfiguration();
        jerseyConfig.setMaxThreads(1024);
        setCommonValues(jerseyConfig);
        return jerseyConfig;
    }

    static JerseyClientConfiguration buildDefaultClientDefaultConfig() {
        final JerseyClientConfiguration jerseyConfig = new JerseyClientConfiguration();
        setCommonValues(jerseyConfig);
        return jerseyConfig;
    }

    private static void setCommonValues(final JerseyClientConfiguration jerseyConfig) {
        jerseyConfig.setTimeout(DEFAULT_TIMEOUT);
        jerseyConfig.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        jerseyConfig.setConnectionRequestTimeout(DEFAULT_CONNECTION_TIMEOUT);
    }

    public void logNonDefaultJerseyClientConfig(final Consumer<String> logger) {
        final JerseyClientConfiguration vanillaConfig = new JerseyClientConfiguration();

        NullSafe.map(configMap).forEach((clientName, jerseyClientConfiguration) -> {
            final List<PropDiff> differences = listDifferences("", vanillaConfig, jerseyClientConfiguration);

            logger.accept(LogUtil.message(
                    "Jersey client differences for '{}':\n{}",
                    clientName,
                    AsciiTable.builder(differences)
                            .withColumn(Column.of("Property", PropDiff::name))
                            .withColumn(Column.of("Default Value", propDiff ->
                                    Objects.toString(propDiff.defaultValue)))
                            .withColumn(Column.of("Actual Value", propDiff ->
                                    Objects.toString(propDiff.value)))
                            .build()));
        });
    }

    private <T> List<PropDiff> listDifferences(final String prefix,
                                               final T vanillaConfig,
                                               final T actualConfig) {
        final Map<String, Prop> defaultProps = PropertyUtil.getProperties(vanillaConfig);

        final List<PropDiff> differences = defaultProps.entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .flatMap(entry -> {
                    final String propName = entry.getKey();
                    final Prop prop = entry.getValue();
                    final Object defaultValue = getPropValue(prop, vanillaConfig);
                    final Object actualValue = getPropValue(prop, actualConfig);

                    if (!Objects.equals(defaultValue, actualValue)) {
                        if (propName.equals("tls")) {
                            if (defaultValue == null && actualValue != null) {
                                return listDifferences("tls.", new TlsConfiguration(), actualValue).stream();
                            } else if (defaultValue != null) {
                                return listDifferences("tls.", defaultValue, actualValue).stream();
                            } else {
                                return Stream.empty();
                            }
                        } else if (propName.equals("proxy")) {
                            if (defaultValue == null && actualValue != null) {
                                return listDifferences("proxy.", new ProxyConfiguration(), actualValue).stream();
                            } else if (defaultValue != null) {
                                return listDifferences("proxy.", defaultValue, actualValue).stream();
                            } else {
                                return Stream.empty();
                            }
                        } else {
                            return Stream.of(new PropDiff(prefix + propName, defaultValue, actualValue));
                        }
                    } else {
                        return Stream.empty();
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        return differences;

    }


    // --------------------------------------------------------------------------------


    private record PropDiff(String name, Object defaultValue, Object value) {

    }
}
