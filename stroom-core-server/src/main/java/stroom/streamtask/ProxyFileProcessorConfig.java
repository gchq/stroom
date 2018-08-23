package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.api.PropertyService;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;

class ProxyFileProcessorConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFileProcessorConfig.class);

    private final static int DEFAULT_MAX_AGGREGATION = 10000;
    private static final long DEFAULT_MAX_STREAM_SIZE = ModelStringUtil.parseIECByteSizeString("10G");

    private final PropertyService propertyService;

    @Inject
    ProxyFileProcessorConfig(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    int getMaxAggregation() {
        return propertyService.getIntProperty("stroom.maxAggregation", DEFAULT_MAX_AGGREGATION);
    }

    long getMaxStreamSize() {
        return getByteSize(propertyService.getProperty("stroom.maxStreamSize"), DEFAULT_MAX_STREAM_SIZE);
    }

    private long getByteSize(final String propertyValue, final long defaultValue) {
        Long value = null;
        try {
            value = ModelStringUtil.parseIECByteSizeString(propertyValue);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (value == null) {
            value = defaultValue;
        }

        return value;
    }
}
