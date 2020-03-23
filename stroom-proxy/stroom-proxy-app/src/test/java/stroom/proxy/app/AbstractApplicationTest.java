package stroom.proxy.app;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractApplicationTest.class);

    private static final Config config;

    static {
        config = loadYamlFile("proxy.yml");
    }


    static Config getConfig() {
        return config;
    }

    private static Config readConfig(final Path configFile) {
        final ConfigurationSourceProvider configurationSourceProvider = new SubstitutingSourceProvider(
            new FileConfigurationSourceProvider(),
            new EnvironmentVariableSubstitutor(false));

        final ConfigurationFactoryFactory<Config> configurationFactoryFactory = new DefaultConfigurationFactoryFactory<>();

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
