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

package stroom.pipeline.refdata;

import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaSet;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class EffectiveStreamService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveStreamService.class);

    private EffectiveStreamCache effectiveStreamCache;

    @Inject
    public EffectiveStreamService(final EffectiveStreamCache effectiveStreamCache) {
        this.effectiveStreamCache = effectiveStreamCache;
    }

//    NavigableSet<EffectiveMeta> get(final EffectiveStreamKey effectiveStreamKey) {
//        return effectiveStreamCache.get(effectiveStreamKey);
//    }

    /**
     * Find the most recent stream that is older than or equal to {@code time} or empty if
     * there isn't one
     */
    Optional<EffectiveMeta> determineEffectiveStream(final PipelineReference pipelineReference,
                                                     final long time,
                                                     final ReferenceDataResult result) {

        LOGGER.trace(() -> LogUtil.message("determineEffectiveStream({}, {})",
                pipelineReference, Instant.ofEpochMilli(time)));

        // Create a key to find a set of effective times in the pool.
        final String feedName = pipelineReference.getFeed().getName();
        final String streamType = pipelineReference.getStreamType();
        final EffectiveStreamKey effectiveStreamKey = EffectiveStreamKey.forLookupTime(feedName, streamType, time);

        // Try and fetch a tree set of effective streams for this key.
        final EffectiveMetaSet effectiveStreams = effectiveStreamCache.get(effectiveStreamKey);

        final Optional<EffectiveMeta> optEffectiveStream;

        if (effectiveStreams != null && !effectiveStreams.isEmpty()) {
            result.logLazyTemplate(Severity.INFO,
                    "Found {} potential effective stream{} (spanning {} => {}) " +
                    "for feed: '{}', type: '{}', window: {} => {}",
                    () -> Arrays.asList(
                            effectiveStreams.size(),
                            StringUtil.pluralSuffix(effectiveStreams.size()),
                            effectiveStreams.first().map(EffectiveMeta::getEffectiveTime),
                            effectiveStreams.last().map(EffectiveMeta::getEffectiveTime),
                            feedName,
                            streamType,
                            Instant.ofEpochMilli(effectiveStreamKey.getFromMs()),
                            Instant.ofEpochMilli(effectiveStreamKey.getToMs())));

            if (LOGGER.isTraceEnabled()) {
                final String streams = effectiveStreams.stream()
                        .map(effectiveMeta -> "  " + effectiveMeta.toString())
                        .collect(Collectors.joining("\n"));
                LOGGER.trace("For key {}, comparing time: {} to effective streams:\n{}",
                        effectiveStreamKey, Instant.ofEpochMilli(time), streams);
            }

            // Try and find the stream before the requested time that is less
            // than or equal to it.
            optEffectiveStream = effectiveStreams.findLatestBefore(time);
        } else {
            // No need to log here as it will get logged by the caller
            optEffectiveStream = Optional.empty();
        }

        if (optEffectiveStream.isEmpty()) {
            result.logLazyTemplate(
                    Severity.WARNING,
                    "No effective stream can be found for feed '{}', stream type '{}' " +
                    "and lookup time '{}'. " +
                    "Check a reference data stream exists with an effective time that is before the " +
                    "lookup time.",
                    () -> Arrays.asList(
                            feedName,
                            streamType,
                            Instant.ofEpochMilli(time).toString()));
        }
        LOGGER.debug("Determined optEffectiveStream to be {}", optEffectiveStream);
        return optEffectiveStream;
    }

    /**
     * For testing
     */
    void setEffectiveStreamCache(final EffectiveStreamCache effectiveStreamcache) {
        this.effectiveStreamCache = effectiveStreamcache;
    }
}
