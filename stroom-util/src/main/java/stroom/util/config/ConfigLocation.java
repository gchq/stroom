package stroom.util.config;

import jakarta.inject.Singleton;

import java.nio.file.Path;

/**
 * Allows to use guice to pass round the config location
 */
@Singleton
public class ConfigLocation {

    private final Path configFilePath;

    public ConfigLocation(final Path configFilePath) {
        this.configFilePath = configFilePath;
    }

    public Path getConfigFilePath() {
        return configFilePath;
    }
}
