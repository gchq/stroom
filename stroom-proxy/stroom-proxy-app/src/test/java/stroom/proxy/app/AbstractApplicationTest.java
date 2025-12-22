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

package stroom.proxy.app;

import stroom.test.common.TestResourceLocks;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.HomeDirProviderImpl;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.io.TempDirProviderImpl;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.client.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@ResourceLock(TestResourceLocks.STROOM_PROXY_APP_PORT_8090)
public abstract class AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractApplicationTest.class);

    private Config config;
    private Client client = null;
    private HomeDirProvider homeDirProvider;
    private TempDirProvider tempDirProvider;
    private PathCreator pathCreator;

    // This is needed by DropwizardExtensionsSupport to fire up the proxy app
    // If we were always using the same config or all config was hot changeable then we could
    // make this static which would be much faster as we would only need to spin it up once.
    @RegisterExtension
    private final DropwizardAppExtension<Config> dropwizard = getDropwizardAppExtension();

    private DropwizardAppExtension<Config> getDropwizardAppExtension() {
        final Config config = getConfig();

        return new DropwizardAppExtension<>(
                getAppClass(),
                config);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        dropwizard.before();
        client = dropwizard.client();
//        MetricsUtil.clearRegistry();
    }

    @AfterEach
    void tearDown() {
        dropwizard.after();

        // make sure these get cleared else they may break other tests that don't use system props
        System.clearProperty(HomeDirProvider.PROP_STROOM_HOME);
        System.clearProperty(TempDirProvider.PROP_STROOM_TEMP);
//        MetricsUtil.clearRegistry();
    }

    // Subclasses can override this
    protected Class<? extends Application<Config>> getAppClass() {
        return App.class;
    }

    protected ProxyPathConfig createProxyPathConfig() {
        final Path temp;
        try {
            temp = Files.createTempDirectory("stroom-proxy");
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating temp dir"), e);
        }

        final Path dataDir = temp.resolve("data").toAbsolutePath().normalize();
        final Path homeDir = temp.resolve("home").toAbsolutePath().normalize();
        final Path tempDir = temp.resolve("temp").toAbsolutePath().normalize();
        FileUtil.ensureDirExists(dataDir);
        FileUtil.ensureDirExists(homeDir);
        FileUtil.ensureDirExists(tempDir);

        final String dataDirStr = dataDir.toString();
        final String homeDirStr = homeDir.toString();
        final String tempDirStr = tempDir.toString();

        // Set the home/temp sys props so the validator in App can validate paths relative to these.
        System.setProperty(HomeDirProvider.PROP_STROOM_HOME, homeDirStr);
        System.setProperty(TempDirProvider.PROP_STROOM_TEMP, tempDirStr);

        return new ProxyPathConfig(dataDirStr, homeDirStr, tempDirStr);
    }

    private void setupConfig() {
        // Need to run this first as it sets up the system props for stroom home/temp
        final ProxyPathConfig proxyPathConfig = createProxyPathConfig();

        config = loadYamlFile("proxy-dev.yml");

        final ProxyConfig proxyConfigOverride = getProxyConfigOverride();
        if (proxyConfigOverride != null) {
            config.setProxyConfig(proxyConfigOverride);
        } else {
            // Can't use Map.of() due to null value
            final Map<PropertyPath, Object> propValueMap = new HashMap<>();
            propValueMap.put(ProxyConfig.buildPath(ProxyConfig.PROP_NAME_PATH), proxyPathConfig);

            // Add any overrides from the sub-classes
            propValueMap.putAll(getPropertyValueOverrides());

            final ProxyConfig modifiedProxyConfig = AbstractConfigUtil.mutateTree(
                    config.getProxyConfig(),
                    ProxyConfig.ROOT_PROPERTY_PATH,
                    propValueMap);

            config.setProxyConfig(modifiedProxyConfig);

        }
        // Remove any TLS config as wire mock is http only
        NullSafe.map(config.getJerseyClients())
                .values()
                .forEach(jerseyClientConfig -> jerseyClientConfig.setTlsConfiguration(null));

        ensureDirectories(config.getProxyConfig());
    }

    private void ensureDirectories(final ProxyConfig proxyConfig) {

        LOGGER.info("Ensuring configured directories exist");
        homeDirProvider = new HomeDirProviderImpl(proxyConfig.getPathConfig());
        tempDirProvider = new TempDirProviderImpl(proxyConfig.getPathConfig(), homeDirProvider);
        pathCreator = new SimplePathCreator(homeDirProvider, tempDirProvider);

        final Consumer<String> createDirConsumer = ThrowingConsumer.unchecked(dirStr -> {
            final Path path = pathCreator.toAppPath(dirStr);
            LOGGER.info("Ensuring dir {} exists", path.toAbsolutePath().normalize());
            Files.createDirectories(path);
        });

        NullSafe.consume(proxyConfig.getPathConfig(), ProxyPathConfig::getData, createDirConsumer);
        NullSafe.consume(proxyConfig.getContentDir(), createDirConsumer);

        if (NullSafe.hasItems(proxyConfig.getForwardFileDestinations())) {
            proxyConfig.getForwardFileDestinations().forEach(forwardConfig -> {
                if (!NullSafe.isBlankString(forwardConfig.getPath())) {
                    createDirConsumer.accept(forwardConfig.getPath());
                }
            });
        }
    }

    protected ProxyConfig getProxyConfigOverride() {
        return null;
    }

    protected Map<PropertyPath, Object> getPropertyValueOverrides() {
        // Override this method if you want to add more prop overrides
        return Collections.emptyMap();
    }


    Config getConfig() {
        if (config == null) {
            setupConfig();
        }
        return config;
    }

    /**
     * @return A client for sending requests to the running server.
     */
    Client getClient() {
        return client;
    }

    public DropwizardAppExtension<Config> getDropwizard() {
        return dropwizard;
    }

    private static Config readConfig(final Path configFile) {
        final ConfigurationSourceProvider configurationSourceProvider = ProxyYamlUtil.createConfigurationSourceProvider(
                new FileConfigurationSourceProvider(),
                true);

        final ConfigurationFactoryFactory<Config> configurationFactoryFactory =
                new DefaultConfigurationFactoryFactory<>();

        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
                .create(
                        Config.class,
                        io.dropwizard.jersey.validation.Validators.newValidator(),
                        Jackson.newObjectMapper(),
                        "dw");

        final Config config;
        try {
            config = configurationFactory.build(configurationSourceProvider, configFile.toAbsolutePath().toString());
        } catch (final ConfigurationException | IOException e) {
            throw new RuntimeException(LogUtil.message("Error parsing configuration from file {}",
                    configFile.toAbsolutePath()), e);
        }

        return config;
    }

    private static Config loadYamlFile(final String filename) {
        final Path path = getStroomProxyAppFile(filename);

        return readConfig(path);
    }

    private static Path getStroomProxyAppFile(final String filename) {
        final String codeSourceLocation = App.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        LOGGER.info(codeSourceLocation);

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-proxy-app")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.resolve(filename);
        }

        if (path == null) {
            throw new RuntimeException("Unable to find " + filename);
        }
        return path;
    }

}
