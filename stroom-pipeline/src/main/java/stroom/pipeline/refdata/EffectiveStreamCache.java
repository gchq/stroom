/*
 * Copyright 2016 Crown Copyright
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

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.MetaService;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.Period;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

// TODO: 14/09/2022 Ideally this class ought to listen for events each time a strm is created/deleted
//  or a feed is deleted and then evict the appropriate keys, but that is a LOT of events going over the
//  cluster.
@Singleton
public class EffectiveStreamCache implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveStreamCache.class);

    private static final String CACHE_NAME = "Reference Data - Effective Stream Cache";

    private final LoadingStroomCache<EffectiveStreamKey, NavigableSet<EffectiveStream>> cache;
    private final MetaService metaService;
    private final EffectiveStreamInternPool internPool;
    private final SecurityContext securityContext;

    @Inject
    EffectiveStreamCache(final CacheManager cacheManager,
                         final MetaService metaService,
                         final EffectiveStreamInternPool internPool,
                         final SecurityContext securityContext,
                         final Provider<ReferenceDataConfig> referenceDataConfigProvider) {
        this.metaService = metaService;
        this.internPool = internPool;
        this.securityContext = securityContext;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> referenceDataConfigProvider.get().getEffectiveStreamCache(),
                this::create);
    }

    public NavigableSet<EffectiveStream> get(final EffectiveStreamKey effectiveStreamKey) {
        if (effectiveStreamKey.getFeed() == null) {
            throw new ProcessException("No feed has been specified for reference data lookup");
        }
        if (effectiveStreamKey.getStreamType() == null) {
            throw new ProcessException("No stream type has been specified for reference data lookup");
        }

        final NavigableSet<EffectiveStream> effectiveStreams = cache.get(effectiveStreamKey);
//        LOGGER.trace(() -> LogUtil.message("get({}) - returned {} streams",
//                effectiveStreamKey, effectiveStreams.size()));
        return effectiveStreams;
    }

    protected NavigableSet<EffectiveStream> create(final EffectiveStreamKey key) {
        return securityContext.asProcessingUserResult(() -> {
            NavigableSet<EffectiveStream> effectiveStreamSet = Collections.emptyNavigableSet();

            try {
                LOGGER.debug("Creating effective stream set, key: {}", key);

                // Limit the stream set to the requested effective time window.
                final Period window = new Period(key.getFromMs(), key.getToMs());

                // Only find streams for the supplied feed and stream type.
                final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria(
                        window,
                        key.getFeed(),
                        key.getStreamType());

                LOGGER.debug("Using criteria: {}", criteria);

                // Hit the DB to find all the streams matching our criteria
                final Set<EffectiveMeta> effectiveMetaSet = metaService.findEffectiveData(criteria);

                // Build them into a set with one stream per eff time
                effectiveStreamSet = buildEffectiveStreamSet(key, effectiveMetaSet);

                // Intern the effective stream set, so we only have one identical
                // copy in memory.
                if (internPool != null) {
                    effectiveStreamSet = internPool.intern(effectiveStreamSet);
                }

                if (LOGGER.isDebugEnabled()) {
                    final String streamsStr;
                    if (effectiveStreamSet.size() > 20) {
                        final String streamsStr1 = effectiveStreamSet.stream()
                                .limit(19)
                                .map(EffectiveStream::getStreamId)
                                .map(strm -> Long.toString(strm))
                                .collect(Collectors.joining(", "));
                        streamsStr = streamsStr1 + "...TRUNCATED..." + effectiveStreamSet.last().getStreamId();
                    } else {
                        streamsStr = effectiveStreamSet.stream()
                                .map(EffectiveStream::getStreamId)
                                .map(strm -> Long.toString(strm))
                                .collect(Collectors.joining(", "));
                    }
                    LOGGER.debug("Created effective stream set of size {}, key: {}, streams: {}",
                            effectiveStreamSet.size(), key, streamsStr);
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Error creating effective stream set for " + key + " - " + e.getMessage(), e);
            }

            return effectiveStreamSet;
        });
    }

    private NavigableSet<EffectiveStream> buildEffectiveStreamSet(final EffectiveStreamKey key,
                                                                  final Set<EffectiveMeta> effectiveMetaSet) {
        final NavigableSet<EffectiveStream> effectiveStreamSet;
        if (NullSafe.isEmptyCollection(effectiveMetaSet)) {
            LOGGER.debug("No effective streams for key {}", key);
            effectiveStreamSet = Collections.emptyNavigableSet();
        } else {
            final Set<String> invalidFeedNames = effectiveMetaSet.stream()
                    .map(EffectiveMeta::getFeedName)
                    .filter(feedName -> !Objects.equals(feedName, key.getFeed()))
                    .collect(Collectors.toSet());

            if (!invalidFeedNames.isEmpty()) {
                throw new RuntimeException(LogUtil.message("Found incorrect feed names {} for key {}",
                        invalidFeedNames, key));
            }

            final Set<String> invalidTypes = effectiveMetaSet.stream()
                    .map(EffectiveMeta::getTypeName)
                    .filter(typeName -> !Objects.equals(typeName, key.getStreamType()))
                    .collect(Collectors.toSet());

            if (!invalidTypes.isEmpty()) {
                throw new RuntimeException(LogUtil.message("Found incorrect stream type names {} for key {}",
                        invalidTypes, key));
            }

            final Map<Long, List<EffectiveMeta>> map = effectiveMetaSet.stream()
                    .collect(Collectors.groupingBy(
                            EffectiveMeta::getEffectiveMs));

            effectiveStreamSet = new TreeSet<>();
            map.forEach((effectiveMs, effectiveMetaList) -> {
                final int countPerEffectiveMs = effectiveMetaList.size();
                if (countPerEffectiveMs > 0) {
                    // Get the latest stream from the list of dups
                    final EffectiveMeta effectiveMeta = effectiveMetaList.stream()
                            .max(Comparator.comparing(EffectiveMeta::getId))
                            .get();

                    if (countPerEffectiveMs > 1) {
                        LOGGER.warn("Multiple reference streams [{}] from feed '{}' found with the same " +
                                        "effective time {}. Only the latest stream, {}, will be used.",
                                effectiveMetaList.stream()
                                        .map(EffectiveMeta::getId)
                                        .map(val -> Long.toString(val))
                                        .collect(Collectors.joining(", ")),
                                key.getFeed(),
                                Instant.ofEpochMilli(effectiveMs),
                                effectiveMeta.getId());
                    }

                    LOGGER.trace("Adding effective stream {} against key {}", effectiveMeta, key);
                    effectiveStreamSet.add(new EffectiveStream(effectiveMeta));
                }
            });
        }
        LOGGER.debug(LogUtil.message("Created effectiveStreamSet containing {} streams for key: {}",
                effectiveStreamSet.size(),
                key));
        return effectiveStreamSet;
    }

    long size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
