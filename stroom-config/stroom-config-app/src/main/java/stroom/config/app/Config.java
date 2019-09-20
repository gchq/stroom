package stroom.config.app;

import io.dropwizard.Configuration;
import stroom.config.app.AppConfig;

public class Config extends Configuration {
    private AppConfig appConfig;

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public void setAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }
}
