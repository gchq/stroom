package stroom.data.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.shared.Meta;
import stroom.data.meta.shared.MetaDataSource;
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
    static Map<String, Object> createAttributeMap(final Meta stream, final Map<String, String> attributeMap) {
        final Map<String, Object> map = new HashMap<>();

        if (stream != null) {
            map.put(MetaDataSource.STREAM_ID, stream.getId());
            map.put(MetaDataSource.CREATE_TIME, stream.getCreateMs());
            map.put(MetaDataSource.EFFECTIVE_TIME, stream.getEffectiveMs());
            map.put(MetaDataSource.STATUS_TIME, stream.getStatusMs());
            map.put(MetaDataSource.STATUS, stream.getStatus().getDisplayValue());
            if (stream.getParentDataId() != null) {
                map.put(MetaDataSource.PARENT_STREAM_ID, stream.getParentDataId());
            }
            if (stream.getTypeName() != null) {
                map.put(MetaDataSource.STREAM_TYPE_NAME, stream.getTypeName());
            }
            final String feedName = stream.getFeedName();
            if (feedName != null) {
                map.put(MetaDataSource.FEED_NAME, feedName);
            }
            final String pipelineUuid = stream.getPipelineUuid();
            map.put(MetaDataSource.PIPELINE_UUID, pipelineUuid);
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaDataSource.getExtendedFields().forEach(field -> {
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
