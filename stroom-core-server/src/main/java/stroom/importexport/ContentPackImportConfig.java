package stroom.importexport;

import stroom.properties.api.PropertyService;

import javax.inject.Inject;

class ContentPackImportConfig {
    static final String AUTO_IMPORT_ENABLED_PROP_KEY = "stroom.contentPackImportEnabled";

    private final PropertyService propertyService;

    @Inject
    ContentPackImportConfig(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    boolean isEnabled() {
        return propertyService.getBooleanProperty(AUTO_IMPORT_ENABLED_PROP_KEY, true);
    }
}
