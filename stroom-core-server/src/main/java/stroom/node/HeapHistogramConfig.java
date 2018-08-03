package stroom.node;

import com.google.inject.Inject;
import stroom.properties.api.PropertyService;

class HeapHistogramConfig {
    static final String CLASS_NAME_MATCH_REGEX_PROP_KEY = "stroom.node.status.heapHistogram.classNameMatchRegex";
    static final String ANON_ID_REGEX_PROP_KEY = "stroom.node.status.heapHistogram.classNameReplacementRegex";
    static final String JMAP_EXECUTABLE_PROP_KEY = "stroom.node.status.heapHistogram.jMapExecutable";

    private PropertyService propertyService;

    @Inject
    HeapHistogramConfig(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    String getClassNameMatchRegex() {
        return propertyService.getProperty(CLASS_NAME_MATCH_REGEX_PROP_KEY);
    }

    String getExecutable() {
        return propertyService.getProperty(JMAP_EXECUTABLE_PROP_KEY);
    }

    String getAnonymousIdRegex() {
        return propertyService.getProperty(ANON_ID_REGEX_PROP_KEY);
    }
}
