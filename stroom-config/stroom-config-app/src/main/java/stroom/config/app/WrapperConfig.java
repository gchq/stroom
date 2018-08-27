package stroom.config.app;

import stroom.config.app.AppConfig;

public class WrapperConfig {
    private AppConfig appConfig;

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public void setAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }
}
