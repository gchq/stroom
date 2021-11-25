package stroom.config.app;

import java.nio.file.Path;

public interface ConfigHolder {

    /**
     * @return The de-serialised form of the config.yml file which is likely to be sparse.
     * This {@link AppConfig} is essentially only the yaml overrides and should not be injected
     * into classes that need config values.
     */
    AppConfig getAppConfig();

    Path getConfigFile();
}
