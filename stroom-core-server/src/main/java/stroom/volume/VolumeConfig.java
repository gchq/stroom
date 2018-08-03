package stroom.volume;

import com.google.inject.Inject;
import stroom.properties.api.PropertyService;

public class VolumeConfig {
    /**
     * How many permanent copies should we keep?
     */
    public static final String PROP_RESILIENT_REPLICATION_COUNT = "stroom.streamstore.resilientReplicationCount";

    /**
     * Whether a default volume should be created on application start, but only if other volumes don't already exist
     */
    static final String PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP = "stroom.volumes.createDefaultOnStart";

    /**
     * Should we try to write to local volumes if possible?
     */
    private static final String PROP_PREFER_LOCAL_VOLUMES = "stroom.streamstore.preferLocalVolumes";

    /**
     * How should we select volumes to use?
     */
    private static final String PROP_VOLUME_SELECTOR = "stroom.streamstore.volumeSelector";

    private static final int DEFAULT_RESILIENT_REPLICATION_COUNT = 1;
    private static final boolean DEFAULT_PREFER_LOCAL_VOLUMES = false;

    private PropertyService propertyService;

    @Inject
    VolumeConfig(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    boolean isCreateOnStartup() {
        return propertyService.getBooleanProperty(PROP_CREATE_DEFAULT_VOLUME_ON_STARTUP, false);
    }

    int getResilientReplicationCount() {
        return propertyService.getIntProperty(PROP_RESILIENT_REPLICATION_COUNT,
                DEFAULT_RESILIENT_REPLICATION_COUNT);
    }

    boolean isPreferLocalVolumes() {
        return propertyService.getBooleanProperty(PROP_PREFER_LOCAL_VOLUMES, DEFAULT_PREFER_LOCAL_VOLUMES);
    }

    String getVolumeSelector() {
        return propertyService.getProperty(PROP_VOLUME_SELECTOR);
    }
}
