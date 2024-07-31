/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.shared;

import stroom.docref.DocRef;
import stroom.util.shared.string.CIKey;

import java.util.HashMap;
import java.util.Map;

public class MetaAttributeMapUtil {

    private MetaAttributeMapUtil() {
        // Utility.
    }

    /**
     * Turns a stream attribute map object into a generic map of attributes for use by an expression filter.
     */
    public static Map<CIKey, Object> createAttributeMap(final Meta meta) {
        final Map<CIKey, Object> map = new HashMap<>();

        if (meta != null) {
            // Non grouped fields
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                map.put(MetaFields.FEED.getFldNameAsCIKey(), feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            if (pipelineUuid != null) {
                map.put(MetaFields.PIPELINE.getFldNameAsCIKey(), new DocRef("Pipeline", pipelineUuid));
            }
            if (meta.getStatus() != null) {
                map.put(MetaFields.STATUS.getFldNameAsCIKey(), meta.getStatus().getDisplayValue());
            }
            if (meta.getTypeName() != null) {
                map.put(MetaFields.TYPE.getFldNameAsCIKey(), meta.getTypeName());
            }

            // Id's
            map.put(MetaFields.ID.getFldNameAsCIKey(), meta.getId());
            if (meta.getParentMetaId() != null) {
                map.put(MetaFields.PARENT_ID.getFldNameAsCIKey(), meta.getParentMetaId());
            }

            // Times
            map.put(MetaFields.CREATE_TIME.getFldNameAsCIKey(), meta.getCreateMs());
            map.put(MetaFields.EFFECTIVE_TIME.getFldNameAsCIKey(), meta.getEffectiveMs());
            map.put(MetaFields.STATUS_TIME.getFldNameAsCIKey(), meta.getStatusMs());

//            FIELDS.add(META_INTERNAL_PROCESSOR_ID);
//            FIELDS.add(META_PROCESSOR_FILTER_ID);
//            FIELDS.add(META_PROCESSOR_TASK_ID);


//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

//        MetaFields.getExtendedFields().forEach(field -> {
//            final String value = attributeMap.get(field.getName());
//            if (value != null) {
//                try {
//                    switch (field.getFieldType()) {
//                        case TEXT:
//                            map.put(field.getName(), value);
//                            break;
//                        case DATE:
//                            map.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
//                            break;
//                        case DOC_REF:
//                            attributeMap.put(field.getName(), value);
//                            break;
//                        case ID:
//                        case LONG:
//                            map.put(field.getName(), Long.valueOf(value));
//                            break;
//                    }
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            }
//        });
        return map;
    }
}
