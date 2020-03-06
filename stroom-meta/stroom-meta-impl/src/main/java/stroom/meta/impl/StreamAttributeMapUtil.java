package stroom.meta.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datasource.api.v2.FieldTypes;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
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
            map.put(MetaFields.ID.getName(), meta.getId());
            map.put(MetaFields.CREATE_TIME.getName(), meta.getCreateMs());
            map.put(MetaFields.EFFECTIVE_TIME.getName(), meta.getEffectiveMs());
            map.put(MetaFields.STATUS_TIME.getName(), meta.getStatusMs());
            map.put(MetaFields.STATUS.getName(), meta.getStatus().getDisplayValue());
            if (meta.getParentMetaId() != null) {
                map.put(MetaFields.PARENT_ID.getName(), meta.getParentMetaId());
            }
            if (meta.getTypeName() != null) {
                map.put(MetaFields.TYPE_NAME.getName(), meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                map.put(MetaFields.FEED_NAME.getName(), feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            if (pipelineUuid != null) {
                map.put(MetaFields.PIPELINE.getName(), new DocRef("Pipeline", pipelineUuid));
            }
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaFields.getExtendedFields().forEach(field -> {
            final String value = attributeMap.get(field.getName());
            if (value != null) {
                try {
                    switch (field.getType()) {
                        case FieldTypes.TEXT:
                            map.put(field.getName(), value);
                            break;
                        case FieldTypes.DATE:
                            map.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
                            break;
                        case FieldTypes.DOC_REF:
                            attributeMap.put(field.getName(), value);
                            break;
                        case FieldTypes.ID:
                        case FieldTypes.LONG:
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
