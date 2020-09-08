package stroom.config.app;

import java.nio.file.Path;

public interface ConfigHolder {
    AppConfig getAppConfig();

    Path getConfigFile();
}
