package stroom.importexport.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@JsonPropertyOrder(alphabetic = true)
public class ContentPackImportConfig extends AbstractConfig {

    private final boolean enabled;
    private final String importDirectory;

    public ContentPackImportConfig() {
        enabled = false;
        importDirectory = "content_pack_import";
    }

    @JsonCreator
    public ContentPackImportConfig(@JsonProperty("enabled") final boolean enabled,
                                   @JsonProperty("importDirectory") final String importDirectory) {
        this.enabled = enabled;
        this.importDirectory = importDirectory;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If true any content packs found in 'importDirectory' will be imported " +
            "into Stroom. Only intended for use on new Stroom instances to reduce the risk of " +
            "overwriting existing entities.")
    public boolean isEnabled() {
        return enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("When stroom starts, if 'enabled' is set to true, it will attempt to import content " +
            "packs from this directory. If the value is null or the directory does not exist it will be ignored." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getImportDirectory() {
        return importDirectory;
    }

    @Override
    public String toString() {
        return "ContentPackImportConfig{" +
                "enabled=" + enabled +
                ", importDirectory='" + importDirectory + '\'' +
                '}';
    }
}
