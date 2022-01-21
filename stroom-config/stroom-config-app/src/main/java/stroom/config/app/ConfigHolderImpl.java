package stroom.config.app;

import java.nio.file.Path;

public class ConfigHolderImpl implements ConfigHolder {

    private final AppConfig appConfig;
    private final Path configFile;

    public ConfigHolderImpl(final AppConfig appConfig, final Path configFile) {
        this.appConfig = appConfig;
        this.configFile = configFile;
    }

    @Override
    public AppConfig getBootStrapConfig() {
        return appConfig;
    }

    @Override
    public Path getConfigFile() {
        return configFile;
    }

    @Override
    public String toString() {
        return "ConfigHolderImpl{" +
                "appConfig=" + appConfig +
                ", configFile=" + configFile +
                '}';
    }
}
