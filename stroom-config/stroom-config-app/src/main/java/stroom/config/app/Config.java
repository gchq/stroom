package stroom.config.app;

import io.dropwizard.Configuration;

public class Config extends Configuration {

    private AppConfig appConfig;

    /**
     * The de-serialised yaml config merged with the compile time defaults to provide
     * a full config tree.
     */
    public AppConfig getAppConfig() {
        return appConfig;
    }

    public void setAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }
}
