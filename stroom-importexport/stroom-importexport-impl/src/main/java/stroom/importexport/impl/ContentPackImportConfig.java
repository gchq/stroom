package stroom.importexport.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

public class ContentPackImportConfig extends AbstractConfig {

    private boolean enabled;
    private String importDirectory = "content_pack_import";

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If true any content packs found in 'importDirectory' will be imported " +
            "into Stroom. Only intended for use on new Stroom instances to reduce the risk of " +
            "overwriting existing entities.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("When stroom starts, if 'enabled' is set to true, it will attempt to import content " +
            "packs from this directory. If the value is null or the directory does not exist it will be ignored." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getImportDirectory() {
        return importDirectory;
    }

    @SuppressWarnings("unused")
    public void setImportDirectory(final String importDirectory) {
        this.importDirectory = importDirectory;
    }

    @Override
    public String toString() {
        return "ContentPackImportConfig{" +
                "enabled=" + enabled +
                ", importDirectory='" + importDirectory + '\'' +
                '}';
    }
}
