package stroom.lifecycle.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class LifecycleConfig implements IsConfig {
    private boolean enabled = true;

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "LifecycleConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
