package stroom.data.meta.impl.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataRow;
import stroom.data.meta.api.MetaDataSource;
import stroom.util.date.DateUtil;

import java.util.HashMap;
import java.util.Map;

class AttributeMapUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeMapUtil.class);

    private AttributeMapUtil() {
        // Utility.
    }

    /**
     * Turns a data row object into a generic map of attributes for use by an expression filter.
     */
    static Map<String, Object> createAttributeMap(final DataRow row) {
        final Map<String, Object> attributeMap = new HashMap<>();

        final Data data = row.getData();
        if (data != null) {
            attributeMap.put(MetaDataSource.STREAM_ID, data.getId());
            attributeMap.put(MetaDataSource.CREATE_TIME, data.getCreateMs());
            attributeMap.put(MetaDataSource.EFFECTIVE_TIME, data.getEffectiveMs());
            attributeMap.put(MetaDataSource.STATUS_TIME, data.getStatusMs());
            attributeMap.put(MetaDataSource.STATUS, data.getStatus().getDisplayValue());
            if (data.getParentDataId() != null) {
                attributeMap.put(MetaDataSource.PARENT_STREAM_ID, data.getParentDataId());
            }
            if (data.getTypeName() != null) {
                attributeMap.put(MetaDataSource.STREAM_TYPE, data.getTypeName());
            }
            final String feedName = data.getFeedName();
            if (feedName != null) {
                attributeMap.put(MetaDataSource.FEED, feedName);
            }
            final String pipelineName = data.getPipelineUuid();
            attributeMap.put(MetaDataSource.PIPELINE, pipelineName);
//            if (processor != null) {
//                final String pipelineUuid = processor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(MetaDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaDataSource.getExtendedFields().forEach(field -> {
            final String value = row.getAttributeValue(field.getName());
            if (value != null) {
                try {
                    switch (field.getType()) {
                        case FIELD:
                            attributeMap.put(field.getName(), value);
                            break;
                        case DATE_FIELD:
                            attributeMap.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
                            break;
                        default:
                            attributeMap.put(field.getName(), Long.valueOf(value));
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        });
        return attributeMap;
    }
}
