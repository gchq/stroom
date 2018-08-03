package stroom.data.meta.impl.db;

import stroom.properties.api.PropertyService;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class MetaValueConfig {
    private final PropertyService propertyService;

    @Inject
    MetaValueConfig(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    long getDeleteAge() {
        final String metaDatabaseAge = propertyService.getProperty("stroom.meta.deleteAge", "30d");
        return ModelStringUtil.parseDurationString(metaDatabaseAge);
    }

    int getDeleteBatchSize() {
        return propertyService.getIntProperty("stroom.meta.deleteBatchSize", 1000);
    }

    int getFlushBatchSize() {
        return propertyService.getIntProperty("stroom.meta.flushBatchSize", 1000);
    }

    boolean isAddAsync() {
        return propertyService.getBooleanProperty("stroom.meta.addAsync", true);
    }
}
