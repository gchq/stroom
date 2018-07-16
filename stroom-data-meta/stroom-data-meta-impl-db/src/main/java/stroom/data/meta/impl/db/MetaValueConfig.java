package stroom.data.meta.impl.db;

import stroom.properties.api.PropertyService;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class MetaValueConfig {
    private final PropertyService stroomPropertyService;

    @Inject
    MetaValueConfig(final PropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    long getDeleteAge() {
        final String metaDatabaseAge = stroomPropertyService.getProperty("stroom.meta.deleteAge", "30d");
        return ModelStringUtil.parseDurationString(metaDatabaseAge);
    }

    int getDeleteBatchSize() {
        return stroomPropertyService.getIntProperty("stroom.meta.deleteBatchSize", 1000);
    }

    int getFlushBatchSize() {
        return stroomPropertyService.getIntProperty("stroom.meta.flushBatchSize", 1000);
    }

    boolean isAddAsync() {
        return stroomPropertyService.getBooleanProperty("stroom.meta.addAsync", true);
    }
}
