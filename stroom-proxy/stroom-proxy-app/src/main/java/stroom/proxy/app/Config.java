package stroom.proxy.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;

import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;

public class Config extends Configuration {

    private ProxyConfig proxyConfig;

    @Valid
    private Map<String, JerseyClientConfiguration> jerseyClients = new HashMap<>();

    @JsonProperty("jerseyClients")
    public Map<String, JerseyClientConfiguration> getJerseyClients() {
        return jerseyClients;
    }

    public void setJerseyClients(final Map<String, JerseyClientConfiguration> jerseyClients) {
        this.jerseyClients = jerseyClients;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public void setProxyConfig(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }
}
