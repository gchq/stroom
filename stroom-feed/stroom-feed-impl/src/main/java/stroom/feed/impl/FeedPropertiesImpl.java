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

package stroom.feed.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.meta.api.MetaService;
import stroom.util.io.StreamUtil;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Optional;

public class FeedPropertiesImpl implements FeedProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedPropertiesImpl.class);

    private final FeedDocCache feedDocCache;
    private final FeedConfig feedConfig;
    private final MetaService metaService;

    @Inject
    FeedPropertiesImpl(final FeedDocCache feedDocCache,
                       final FeedConfig feedConfig,
                       final MetaService metaService) {
        this.feedDocCache = feedDocCache;
        this.feedConfig = feedConfig;
        this.metaService = metaService;
    }

    @Override
    public String getDisplayClassification(final String feedName) {
        final Optional<FeedDoc> optional = feedDocCache.get(feedName);
        final String classification = optional
                .map(FeedDoc::getClassification)
                .filter(c -> !c.trim().isEmpty())
                .orElse(feedConfig.getUnknownClassification());

        return classification.trim().toUpperCase();
    }

    /**
     * @param feedName            The feed name.
     * @param streamTypeName      The name of the stream type, e.g. Raw Reference
     * @param childStreamTypeName The name of the child stream type, e.g. Context, or null for the data child stream
     *                            or if not applicable
     * @return The applicable encoding
     */
    @Override
    public String getEncoding(final String feedName,
                              final String streamTypeName,
                              final String childStreamTypeName) throws UnsupportedEncodingException {

        final Optional<FeedDoc> optionalFeedDoc = feedDocCache.get(feedName);
        if (optionalFeedDoc.isPresent()) {
            final FeedDoc feedDoc = optionalFeedDoc.get();

            if (StreamTypeNames.CONTEXT.equals(childStreamTypeName)) {
                return resolveEncoding(feedName, childStreamTypeName, feedDoc.getContextEncoding());

            } else if (childStreamTypeName == null
                       && metaService.isRaw(streamTypeName)) {
                // Child stream type is null for the data child streams
                return resolveEncoding(feedName, streamTypeName, feedDoc.getEncoding());

            } else {
                return StreamUtil.DEFAULT_CHARSET_NAME;
            }
        }
        return StreamUtil.DEFAULT_CHARSET_NAME;
    }

    private String resolveEncoding(final String feedName,
                                   final String streamTypeName,
                                   final String encoding) throws UnsupportedEncodingException {
        if (encoding != null && !encoding.isBlank()) {
            try {
                if (Charset.isSupported(encoding)) {
                    return encoding;
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }

            final String message = "Unsupported encoding '" +
                                   encoding +
                                   "' for feed '" +
                                   feedName +
                                   "' and type '" +
                                   streamTypeName +
                                   "'. Using default '" +
                                   StreamUtil.DEFAULT_CHARSET_NAME +
                                   "'.";
            LOGGER.debug(message);
            throw new UnsupportedEncodingException(message);
        }

        return StreamUtil.DEFAULT_CHARSET_NAME;
    }

    @Override
    public String getStreamTypeName(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::getStreamType)
                .orElse(StreamTypeNames.RAW_EVENTS);
    }

    @Override
    public boolean isReference(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::isReference)
                .orElse(false);
    }

    @Override
    public boolean exists(final String feedName) {
        return feedDocCache.get(feedName)
                .isPresent();
    }

    @Override
    public FeedStatus getStatus(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::getStatus)
                .orElse(null);
    }

    @Override
    public DocRef getDocRefForName(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::asDocRef)
                .orElse(null);
    }
}
