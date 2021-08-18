package stroom.util.config;

import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Singleton;

/**
 * Allows to use guice to pass round the config location
 */
@Singleton
public class ConfigLocation {

    private final Path configFilePath;

    public ConfigLocation(final Path configFilePath) {
        this.configFilePath = Objects.requireNonNull(configFilePath);
    }

    public Path getConfigFilePath() {
        return configFilePath;
    }
}
