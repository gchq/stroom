package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ForwardHttpPostConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.NullSafe;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractApplicationTest.class);

    private static final Config config;

    static {
        config = loadYamlFile("proxy-dev.yml");

        // If the home/temp paths don't exist then startup will exit, killing the rest of the tests

        final Path temp;
        try {
            temp = Files.createTempDirectory("stroom-proxy");
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error creating temp dir"), e);
        }

        final Path homeDir = temp.resolve("home");
        final Path tempDir = temp.resolve("temp");
        FileUtil.ensureDirExists(homeDir);
        FileUtil.ensureDirExists(tempDir);

        final String homeDirStr = FileUtil.getCanonicalPath(homeDir);
        final String tempDirStr = FileUtil.getCanonicalPath(tempDir);

        final Path proxyHomeDir = Paths.get(homeDirStr);
        final Path proxyTempDir = Paths.get(tempDirStr);
        FileUtil.ensureDirExists(proxyHomeDir);
        FileUtil.ensureDirExists(proxyTempDir);

        final ProxyPathConfig modifiedPathConfig = config.getProxyConfig()
                .getPathConfig()
                .withHome(homeDirStr)
                .withTemp(tempDirStr);

        // The key/trust store paths will not be available in travis so null them out
        final List<ForwardConfig> forwardConfigs = NullSafe.stream(config.getProxyConfig().getForwardDestinations())
                .map(forwardConfig -> {
                    if (forwardConfig instanceof ForwardHttpPostConfig) {
                        return ((ForwardHttpPostConfig) forwardConfig).withSslConfig(null);
                    } else {
                        // keep unchanged
                        return forwardConfig;
                    }
                }).toList();

        // Can't use Map.of() due to null value
        final Map<PropertyPath, Object> propValueMap = new HashMap<>();
        propValueMap.put(ProxyConfig.buildPath(ProxyConfig.PROP_NAME_REST_CLIENT, "tls"), null);
        propValueMap.put(
                ProxyConfig.buildPath(ProxyConfig.PROP_NAME_FORWARD_DESTINATIONS),
                forwardConfigs);
        propValueMap.put(ProxyConfig.buildPath(ProxyConfig.PROP_NAME_PATH), modifiedPathConfig);
        propValueMap.put(ProxyConfig.buildPath(
                ProxyConfig.PROP_NAME_REPOSITORY,
                ProxyRepoConfig.PROP_NAME_STORING_ENABLED), false);

        final ProxyConfig modifiedProxyConfig = AbstractConfigUtil.mutateTree(
                config.getProxyConfig(),
                ProxyConfig.ROOT_PROPERTY_PATH,
                propValueMap);

        config.setProxyConfig(modifiedProxyConfig);
    }

    static Config getConfig() {
        return config;
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
