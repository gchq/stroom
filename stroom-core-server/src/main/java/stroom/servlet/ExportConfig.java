package stroom.servlet;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ExportConfig {
    private boolean enabled;

    @JsonPropertyDescription("Determines if the system will allow configuration to be exported via the export servlet")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
