package stroom.importexport.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ExportConfig extends AbstractConfig implements IsStroomConfig {

    private final boolean enabled;

    public ExportConfig() {
        enabled = false;
    }

    @JsonCreator
    public ExportConfig(@JsonProperty("enabled") final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("Determines if the system will allow configuration to be exported via the export servlet")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
