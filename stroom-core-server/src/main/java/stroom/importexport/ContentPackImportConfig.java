package stroom.importexport;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ContentPackImportConfig {
    private boolean enabled;

    @JsonPropertyDescription("If true any content packs found in 'contentPackImport' will be imported into Stroom. Only intended for use on new Stroom instances to reduce the risk of overwriting existing entities")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
