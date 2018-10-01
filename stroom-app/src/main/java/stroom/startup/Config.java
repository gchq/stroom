package stroom.startup;

import io.dropwizard.Configuration;
import stroom.config.app.AppConfig;
import stroom.proxy.guice.ProxyConfig;

public class Config extends Configuration {
    public enum StartupMode {
        PROXY,
        APP
    }

    private StartupMode mode = StartupMode.APP;
    private AppConfig appConfig;
    private ProxyConfig proxyConfig;

    public StartupMode getMode() {
        return mode;
    }

    public void setMode(final StartupMode mode) {
        this.mode = mode;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public void setAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public void setProxyConfig(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }
}
