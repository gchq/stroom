package stroom.importexport.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class ExportConfig extends AbstractConfig {
    private boolean enabled;

    @JsonPropertyDescription("Determines if the system will allow configuration to be exported via the export servlet")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
