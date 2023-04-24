package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwardFileConfig;
import stroom.proxy.repo.ForwardRetryConfig;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.NullSafe;
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
import stroom.util.shared.PropertyPath;

import io.dropwizard.Application;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import javax.ws.rs.client.Client;

public abstract class AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractApplicationTest.class);

    private Config config;
    private Client client = null;
    private Path homeDir = null;
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
    }

    @AfterEach
    void tearDown() {
        dropwizard.after();

        // make sure these get cleared else they may break other tests that don't use system props
        System.clearProperty(HomeDirProvider.PROP_STROOM_HOME);
        System.clearProperty(TempDirProvider.PROP_STROOM_TEMP);
    }

    // Subclasses can override this
    protected Class<? extends Application<Config>> getAppClass() {
        return App.class;
    }

    public Path getHomeDir() {
        return homeDir;
    }

    protected ProxyPathConfig createProxyPathConfig() {
        final Path temp;
        try {
            temp = Files.createTempDirectory("stroom-proxy");
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating temp dir"), e);
        }

        final Path homeDir = temp.resolve("home").toAbsolutePath().normalize();
        this.homeDir = homeDir;
        final Path tempDir = temp.resolve("temp").toAbsolutePath().normalize();
        FileUtil.ensureDirExists(homeDir);
        FileUtil.ensureDirExists(tempDir);

        final String homeDirStr = homeDir.toString();
        final String tempDirStr = tempDir.toString();

        // Set the home/temp sys props so the validator in App can validate paths relative to these.
        System.setProperty(HomeDirProvider.PROP_STROOM_HOME, homeDirStr);
        System.setProperty(TempDirProvider.PROP_STROOM_TEMP, tempDirStr);

        return new ProxyPathConfig(homeDirStr, tempDirStr);
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
            propValueMap.put(ProxyConfig.buildPath(ProxyConfig.PROP_NAME_REST_CLIENT, "tls"), null);
            propValueMap.put(ProxyConfig.buildPath(ProxyConfig.PROP_NAME_PATH), proxyPathConfig);
            propValueMap.put(ProxyConfig.buildPath(
                    ProxyConfig.PROP_NAME_REPOSITORY,
                    ProxyRepoConfig.PROP_NAME_STORING_ENABLED), false);

            // Add any overrides from the sub-classes
            propValueMap.putAll(getPropertyValueOverrides());

            final ProxyConfig modifiedProxyConfig = AbstractConfigUtil.mutateTree(
                    config.getProxyConfig(),
                    ProxyConfig.ROOT_PROPERTY_PATH,
                    propValueMap);

            config.setProxyConfig(modifiedProxyConfig);
        }

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

        NullSafe.consume(proxyConfig.getProxyRepositoryConfig(), ProxyRepoConfig::getRepoDir, createDirConsumer);
        NullSafe.consume(proxyConfig.getContentDir(), createDirConsumer);
        NullSafe.consume(proxyConfig.getProxyDbConfig(), ProxyDbConfig::getDbDir, createDirConsumer);
        NullSafe.consume(proxyConfig.getForwardRetry(), ForwardRetryConfig::getFailedForwardDir, createDirConsumer);

        if (NullSafe.hasItems(proxyConfig.getForwardDestinations())) {
            proxyConfig.getForwardDestinations().forEach(forwardConfig -> {
                if (forwardConfig instanceof final ForwardFileConfig forwardFileConfig) {
                    if (!NullSafe.isBlankString(forwardFileConfig.getPath())) {
                        createDirConsumer.accept(forwardFileConfig.getPath());
                    }
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

    Client getClient() {
        return client;
    }

    public PathCreator getPathCreator() {
        return pathCreator;
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

        Config config = null;
        try {
            config = configurationFactory.build(configurationSourceProvider, configFile.toAbsolutePath().toString());
        } catch (ConfigurationException | IOException e) {
            throw new RuntimeException(LogUtil.message("Error parsing configuration from file {}",
                    configFile.toAbsolutePath()), e);
        }

        return config;
    }

    private static Config loadYamlFile(final String filename) {
        Path path = getStroomProxyAppFile(filename);

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
