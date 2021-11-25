package stroom.config.app;

import io.dropwizard.Configuration;

public class Config extends Configuration {

    // TODO 25/11/2021 AT: Remove appConfig so DW does not de-ser it. We will do it manually.
    private AppConfig appConfig;

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public void setAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }
}
