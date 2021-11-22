package stroom.importexport.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
public class ExportConfig extends AbstractConfig {

    private static final Boolean ENABLED_DEFAULT = Boolean.TRUE;

    private Boolean enabled;

    @JsonPropertyDescription("Determines if the system will allow configuration to be exported via the export servlet")
    public boolean isEnabled() {
        return Objects.requireNonNullElse(enabled, ENABLED_DEFAULT);
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = Objects.requireNonNullElse(enabled, ENABLED_DEFAULT);
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
