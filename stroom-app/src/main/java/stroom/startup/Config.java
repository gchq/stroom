package stroom.startup;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import stroom.config.app.AppConfig;
import stroom.proxy.guice.ProxyConfig;

public class Config extends Configuration {
    private String mode;
    private AppConfig appConfig;
    private ProxyConfig proxyConfig;

    public String getMode() {
        return mode;
    }

    public void setMode(final String mode) {
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
