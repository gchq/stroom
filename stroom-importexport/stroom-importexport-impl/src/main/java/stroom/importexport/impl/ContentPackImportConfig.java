package stroom.importexport.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class ContentPackImportConfig extends AbstractConfig {
    private boolean enabled;
    private String importDirectory;

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If true any content packs found in 'contentPackImport' will be imported " +
            "into Stroom. Only intended for use on new Stroom instances to reduce the risk of " +
            "overwriting existing entities")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("When stroom starts, if 'enabled' is set to true, it will attempt to import content " +
        "packs from all of the following locations: the directory defined by this property, <stroom jar location>/contentPackImport, " +
        "~/contentPackImport and <stroom.temp>/contentPackImport. If this property is set then it will also look in the supplied directory. " +
        "If any of the directories doesn't exist it will be ignored.")
    public String getImportDirectory() {
        return importDirectory;
    }

    @SuppressWarnings("unused")
    void setImportDirectory(final String importDirectory) {
        this.importDirectory = importDirectory;
    }

    @Override
    public String toString() {
        return "ContentPackImportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
