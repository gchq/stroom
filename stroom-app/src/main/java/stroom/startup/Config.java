package stroom.startup;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import stroom.proxy.guice.ProxyConfig;

public class Config extends Configuration {
    public static boolean superDevMode;
    private SessionCookieConfig sessionCookieConfig = new SessionCookieConfig();
    private String mode;
    private ProxyConfig proxyConfig;
    private String externalConfig = "~/.stroom/stroom.conf";

    @JsonProperty("superDevMode")
    public boolean isSuperDevMode() {
        return superDevMode;
    }

    @JsonProperty("superDevMode")
    public void setSuperDevMode(final boolean superDevMode) {
        Config.superDevMode = superDevMode;
    }

    @JsonProperty("sessionCookie")
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }

    @JsonProperty("sessionCookie")
    public void setSessionCookieConfig(final SessionCookieConfig sessionCookieConfig) {
        this.sessionCookieConfig = sessionCookieConfig;
    }

    @JsonProperty
    public String getMode() {
        return mode;
    }

    @JsonProperty
    public void setMode(final String mode) {
        this.mode = mode;
    }

    @JsonProperty
    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    @JsonProperty
    public void setProxyConfig(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @JsonProperty
    public String getExternalConfig() {
        return externalConfig;
    }

    @JsonProperty
    public void setExternalConfig(final String externalConfig) {
        this.externalConfig = externalConfig;
    }

}
