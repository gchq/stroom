/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class StreamAttributeMapConverter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamAttributeMapConverter.class);

    private final Provider<DocRefInfoService> docRefInfoServiceProvider;

    @Inject
    StreamAttributeMapConverter(final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    /**
     * Turns a stream attribute map object into a generic map of attributes for use by an expression filter.
     */
    Map<String, Object> createAttributeMap(final Meta meta, final Map<String, String> attributeMap) {
        final Map<String, Object> map = new HashMap<>();

        if (meta != null) {
            map.put(MetaFields.ID.getFldName(), meta.getId());
            map.put(MetaFields.CREATE_TIME.getFldName(), meta.getCreateMs());
            map.put(MetaFields.EFFECTIVE_TIME.getFldName(), meta.getEffectiveMs());
            map.put(MetaFields.STATUS_TIME.getFldName(), meta.getStatusMs());
            map.put(MetaFields.STATUS.getFldName(), NullSafe.get(meta.getStatus(), Status::getDisplayValue));
            NullSafe.consume(meta.getParentMetaId(), parentMetaId ->
                    map.put(MetaFields.PARENT_ID.getFldName(), parentMetaId));
            NullSafe.consume(meta.getTypeName(), typeName ->
                    map.put(MetaFields.TYPE.getFldName(), typeName));

            // Need to convert the feed name into a docref as the Feed field is of type DocRef
            final String feedName = meta.getFeedName();
            if (NullSafe.isNonBlankString(feedName)) {
                final List<DocRef> feedDocRefs = docRefInfoServiceProvider.get().findByName(
                        FeedDoc.TYPE, feedName, false);
                if (NullSafe.hasItems(feedDocRefs)) {
                    if (feedDocRefs.size() > 1) {
                        LOGGER.error("More than one feed document found with name '{}'", feedName);
                    }
                    NullSafe.consume(feedDocRefs.getFirst(), feedDocRef ->
                            map.put(MetaFields.FEED.getFldName(), feedDocRef));
                } else {
                    LOGGER.warn(() -> LogUtil.message(
                            "Meta ID {} has non-existent feed with name: '{}'", meta.getId(), feedName));
                }
            }
            NullSafe.consume(meta.getPipelineUuid(), pipelineUuid ->
                    map.put(MetaFields.PIPELINE.getFldName(), PipelineDoc.buildDocRef().uuid(pipelineUuid).build()));
        }

        MetaFields.getExtendedFields().forEach(field -> {
            final String value = attributeMap.get(field.getFldName());
            if (value != null) {
                try {
                    switch (field.getFldType()) {
                        case TEXT -> map.put(field.getFldName(), value);
                        case DATE -> map.put(field.getFldName(), DateUtil.parseNormalDateTimeString(value));
                        case DOC_REF -> attributeMap.put(field.getFldName(), value);
                        case ID, LONG -> map.put(field.getFldName(), Long.valueOf(value));
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error("Error putting field '{}' value '{}' for meta ID {} - {}",
                            field.getFldName(), value, NullSafe.get(meta, Meta::getId), LogUtil.exceptionMessage(e), e);
                }
            }
        });
        LOGGER.debug("createAttributeMap() - returning: {}", map);
        return map;
    }
}
