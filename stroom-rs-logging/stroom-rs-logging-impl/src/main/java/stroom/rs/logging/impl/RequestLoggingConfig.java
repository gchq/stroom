package stroom.rs.logging.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class RequestLoggingConfig extends AbstractConfig {

    private boolean globalLoggingEnabled = false;

    @JsonProperty("globalLoggingEnabled")
    @JsonPropertyDescription("Log additional RESTful service calls (may cause event duplication).")
    public boolean isGlobalLoggingEnabled() {
        return globalLoggingEnabled;
    }

    public void setGlobalLoggingEnabled(final boolean globalLoggingEnabled) {
        this.globalLoggingEnabled = globalLoggingEnabled;
    }

    @Override
    public String toString() {
        return "RequestLoggingConfig{" +
                "globalLoggingEnabled=" + globalLoggingEnabled +
                '}';
    }

}
