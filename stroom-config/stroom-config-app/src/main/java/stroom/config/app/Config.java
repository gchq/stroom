package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;

import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;

// Can't use a JsonCreator for this as the superclass doesn't use 'JsonCreator
@JsonPropertyOrder(alphabetic = true)
public class Config extends Configuration {

    private AppConfig appConfig;

    @Valid
    private Map<String, JerseyClientConfiguration> jerseyClients = new HashMap<>();

    public Config() {
    }

    public Config(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @JsonProperty("jerseyClients")
    public Map<String, JerseyClientConfiguration> getJerseyClients() {
        return jerseyClients;
    }

    public void setJerseyClients(final Map<String, JerseyClientConfiguration> jerseyClients) {
        this.jerseyClients = jerseyClients;
    }

    /**
     * The de-serialised yaml config merged with the compile time defaults to provide
     * a full config tree. Should ONLY be used by classes involved with setting up the
     * config properties. It MUST NOT be used by classes to get configuration values. They
     * should instead inject AppConfig or its descendants.
     */
    @JsonProperty("appConfig")
    public AppConfig getYamlAppConfig() {
        return appConfig;
    }

    public void setYamlAppConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }
}
