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

package stroom.meta.impl;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.string.CIKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    static Map<CIKey, Object> createAttributeMap(final Meta meta,
                                                 final Map<String, String> attributeMap) {
        final Map<CIKey, Object> map = new HashMap<>();

        if (meta != null) {
            map.put(MetaFields.ID.getFldNameAsCIKey(), meta.getId());
            map.put(MetaFields.CREATE_TIME.getFldNameAsCIKey(), meta.getCreateMs());
            map.put(MetaFields.EFFECTIVE_TIME.getFldNameAsCIKey(), meta.getEffectiveMs());
            map.put(MetaFields.STATUS_TIME.getFldNameAsCIKey(), meta.getStatusMs());
            map.put(MetaFields.STATUS.getFldNameAsCIKey(), NullSafe.get(meta.getStatus(), Status::getDisplayValue));
            if (meta.getParentMetaId() != null) {
                map.put(MetaFields.PARENT_ID.getFldNameAsCIKey(), meta.getParentMetaId());
            }
            if (meta.getTypeName() != null) {
                map.put(MetaFields.TYPE.getFldNameAsCIKey(), meta.getTypeName());
            }
            final String feedName = meta.getFeedName();
            if (feedName != null) {
                map.put(MetaFields.FEED.getFldNameAsCIKey(), feedName);
            }
            final String pipelineUuid = meta.getPipelineUuid();
            if (pipelineUuid != null) {
                map.put(MetaFields.PIPELINE.getFldNameAsCIKey(), new DocRef("Pipeline", pipelineUuid));
            }
//            if (streamProcessor != null) {
//                final String pipelineUuid = streamProcessor.getPipelineUuid();
//                if (pipelineUuid != null) {
//                    attributeMap.put(StreamDataSource.PIPELINE, pipelineUuid);
//                }
//            }
        }

        MetaFields.getExtendedFields().forEach(field -> {
            final CIKey fieldKey = field.getFldNameAsCIKey();
            final String value = attributeMap.get(fieldKey.get());
            if (value != null) {
                try {
                    switch (field.getFldType()) {
                        case TEXT, DOC_REF -> map.put(fieldKey, value);
                        case DATE -> map.put(fieldKey, DateUtil.parseNormalDateTimeString(value));
                        case ID, LONG -> map.put(fieldKey, Long.valueOf(value));
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        });
        return map;
    }
}
