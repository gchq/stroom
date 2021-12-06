package stroom.lifecycle.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@JsonPropertyOrder(alphabetic = true)
public class LifecycleConfig extends AbstractConfig {

    private final boolean enabled;

    public LifecycleConfig() {
        enabled = true;
    }

    @JsonCreator
    public LifecycleConfig(@JsonProperty("enabled") final boolean enabled) {
        this.enabled = enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "LifecycleConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
