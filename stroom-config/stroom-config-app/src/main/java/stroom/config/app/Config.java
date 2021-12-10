package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

// Can't use a JsonCreator for this as the superclass doesn't use 'JsonCreator
public class Config extends Configuration {

    private AppConfig appConfig;

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
