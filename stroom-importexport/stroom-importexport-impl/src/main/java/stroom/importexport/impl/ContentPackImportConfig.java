package stroom.importexport.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class ContentPackImportConfig implements IsConfig {
    private boolean enabled;

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

    @Override
    public String toString() {
        return "ContentPackImportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
