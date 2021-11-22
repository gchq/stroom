package stroom.lifecycle.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
public class LifecycleConfig extends AbstractConfig {

    private static final Boolean ENABLED_DEFAULT = Boolean.TRUE;

    private Boolean enabled = ENABLED_DEFAULT;

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Set this to false for development and testing purposes otherwise the Stroom will " +
            "try and process files automatically outside of test cases.")
    public boolean isEnabled() {
        return Objects.requireNonNullElse(enabled, ENABLED_DEFAULT);
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = Objects.requireNonNullElse(enabled, ENABLED_DEFAULT);
    }

    @Override
    public String toString() {
        return "LifecycleConfig{" +
                "enabled=" + enabled +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LifecycleConfig that = (LifecycleConfig) o;
        return Objects.equals(enabled, that.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }
}
