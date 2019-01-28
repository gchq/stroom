package stroom.meta.impl.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.MetaFieldNames;
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
    static Map<String, Object> createAttributeMap(final MetaRow row) {
        final Map<String, Object> attributeMap = new HashMap<>();

        final Meta meta = row.getMeta();
        if (meta != null) {
            attributeMap.put(MetaFieldNames.STREAM_ID, meta.getId());
            attributeMap.put(MetaFieldNames.CREATE_TIME, meta.getCreateMs());
            attributeMap.put(MetaFieldNames.EFFECTIVE_TIME, meta.getEffectiveMs());
            attributeMap.put(MetaFieldNames.STATUS_TIME, meta.getStatusMs());
            attributeMap.put(MetaFieldNames.STATUS, meta.getStatus().getDisplayValue());
            if (meta.getParentDataId() != null) {
                attributeMap.put(MetaFieldNames.PARENT_STREAM_ID, meta.getParentDataId());
            }
            if (meta.getTypeName() != null) {
                attributeMap.put(MetaFieldNames.STREAM_TYPE_NAME, meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                attributeMap.put(MetaFieldNames.FEED_NAME, feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            attributeMap.put(MetaFieldNames.PIPELINE_UUID, pipelineUuid);
//            if (processor != null) {
//                final String pipelineUuid = processor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(MetaDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaFieldNames.getExtendedFields().forEach(field -> {
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
