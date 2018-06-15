package stroom.streamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.shared.StreamDataSource;
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
    static Map<String, Object> createAttributeMap(final Stream stream, final Map<String, String> metaMap) {
        final Map<String, Object> attributeMap = new HashMap<>();

        if (stream != null) {
            attributeMap.put(StreamDataSource.STREAM_ID, stream.getId());
            attributeMap.put(StreamDataSource.CREATE_TIME, stream.getCreateMs());
            attributeMap.put(StreamDataSource.EFFECTIVE_TIME, stream.getEffectiveMs());
            attributeMap.put(StreamDataSource.STATUS_TIME, stream.getStatusMs());
            attributeMap.put(StreamDataSource.STATUS, stream.getStatus().getDisplayValue());
            if (stream.getParentStreamId() != null) {
                attributeMap.put(StreamDataSource.PARENT_STREAM_ID, stream.getParentStreamId());
            }
            if (stream.getStreamTypeName() != null) {
                attributeMap.put(StreamDataSource.STREAM_TYPE, stream.getStreamTypeName());
            }
            final String feedName = stream.getFeedName();
            if (feedName != null) {
                attributeMap.put(StreamDataSource.FEED, feedName);
            }
            final String pipelineName = stream.getPipelineUuid();
            attributeMap.put(StreamDataSource.PIPELINE, pipelineName);
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        StreamDataSource.getExtendedFields().forEach(field -> {
            final String value = metaMap.get(field.getName());
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
