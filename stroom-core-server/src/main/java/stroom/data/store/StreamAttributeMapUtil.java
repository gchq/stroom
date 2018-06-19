package stroom.data.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamDataSource;
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
    static Map<String, Object> createAttributeMap(final Stream stream, final Map<String, String> attributeMap) {
        final Map<String, Object> map = new HashMap<>();

        if (stream != null) {
            map.put(StreamDataSource.STREAM_ID, stream.getId());
            map.put(StreamDataSource.CREATE_TIME, stream.getCreateMs());
            map.put(StreamDataSource.EFFECTIVE_TIME, stream.getEffectiveMs());
            map.put(StreamDataSource.STATUS_TIME, stream.getStatusMs());
            map.put(StreamDataSource.STATUS, stream.getStatus().getDisplayValue());
            if (stream.getParentStreamId() != null) {
                map.put(StreamDataSource.PARENT_STREAM_ID, stream.getParentStreamId());
            }
            if (stream.getStreamTypeName() != null) {
                map.put(StreamDataSource.STREAM_TYPE, stream.getStreamTypeName());
            }
            final String feedName = stream.getFeedName();
            if (feedName != null) {
                map.put(StreamDataSource.FEED, feedName);
            }
            final String pipelineName = stream.getPipelineUuid();
            map.put(StreamDataSource.PIPELINE, pipelineName);
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        StreamDataSource.getExtendedFields().forEach(field -> {
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
                        default:
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
