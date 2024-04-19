package stroom.config.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class CrossModuleConfig extends AbstractConfig implements IsStroomConfig {

    public static final String NAME = "crossModule";
    public static final String PROP_NAME_DB = "db";

    private final CrossModuleDbConfig crossModuleDbConfig;

    public CrossModuleConfig() {
        this.crossModuleDbConfig = new CrossModuleDbConfig();
    }

    @JsonCreator
    public CrossModuleConfig(@JsonProperty(PROP_NAME_DB) final CrossModuleDbConfig crossModuleDbConfig) {
        this.crossModuleDbConfig = crossModuleDbConfig;
    }

    @JsonProperty(PROP_NAME_DB)
    public CrossModuleDbConfig getAppDbConfig() {
        return crossModuleDbConfig;
    }
}
