package stroom.config.app;

import java.nio.file.Path;

public interface ConfigHolder {

    /**
     * @return The de-serialised form of the config.yml file that has been merged with the
     * default config to ensure a full tree.
     * This does NOT include any database overrides so should only be
     * used by classes that need config values that are required in order to start the app,
     * e.g. DB connection details, or those involved in combining yaml/default/db config.
     */
    AppConfig getBootStrapConfig();

    /**
     * @return The path to the config file.
     */
    Path getConfigFile();
}
