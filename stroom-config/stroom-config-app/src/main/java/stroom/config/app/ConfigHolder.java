package stroom.config.app;

import java.nio.file.Path;

public interface ConfigHolder {

    /**
     * @return The de-serialised form of the config.yml file which will be sparse.
     * This {@link AppConfig} is essentially only the yaml overrides and should only be
     * used by classes that need config values that are required in order to start the app,
     * e.g. DB connection details.
     */
    AppConfig getBootStrapConfig();

    /**
     * @return The path to the config file.
     */
    Path getConfigFile();
}
