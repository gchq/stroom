package stroom.data.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.util.date.DateUtil;

import java.util.HashMap;
import java.util.Map;

class StreamAttributeMapUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapUtil.class);

    private StreamAttributeMapUtil() {
        // Utility.
    }

    /**
     * Turns a stream attribute map object into a generic map of attributes for use by an expression filter.
     */
    static Map<String, Object> createAttributeMap(final Meta meta, final Map<String, String> attributeMap) {
        final Map<String, Object> map = new HashMap<>();

        if (meta != null) {
            map.put(MetaFieldNames.ID, meta.getId());
            map.put(MetaFieldNames.CREATE_TIME, meta.getCreateMs());
            map.put(MetaFieldNames.EFFECTIVE_TIME, meta.getEffectiveMs());
            map.put(MetaFieldNames.STATUS_TIME, meta.getStatusMs());
            map.put(MetaFieldNames.STATUS, meta.getStatus().getDisplayValue());
            if (meta.getParentMetaId() != null) {
                map.put(MetaFieldNames.PARENT_ID, meta.getParentMetaId());
            }
            if (meta.getTypeName() != null) {
                map.put(MetaFieldNames.TYPE_NAME, meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                map.put(MetaFieldNames.FEED_NAME, feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            map.put(MetaFieldNames.PIPELINE_UUID, pipelineUuid);
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaFieldNames.getExtendedFields().forEach(field -> {
            final String value = attributeMap.get(field.getName());
            if (value != null) {
                try {
                    switch (field.getType()) {
                        case FIELD:
                            map.put(field.getName(), value);
                            break;
                        case DATE_FIELD:
                            map.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
                            break;
                        case DOC_REF:
                            attributeMap.put(field.getName(), value);
                            break;
                        case ID:
                        case NUMERIC_FIELD:
                            map.put(field.getName(), Long.valueOf(value));
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        });
        return map;
    }
}
