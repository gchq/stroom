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
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

// TODO: 14/09/2022 Ideally this class ought to listen for events each time a strm is created/deleted
//  or a feed is deleted and then evict the appropriate keys, but that is a LOT of events going over the
//  cluster.
@Singleton
public class EffectiveStreamCache implements Clearable, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveStreamCache.class);

    private static final String CACHE_NAME = "Reference Data - Effective Stream Cache";

    private final LoadingStroomCache<EffectiveStreamKey, NavigableSet<EffectiveMeta>> cache;
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

    public NavigableSet<EffectiveMeta> get(final EffectiveStreamKey effectiveStreamKey) {
        if (effectiveStreamKey.getFeed() == null) {
            throw new ProcessException("No feed has been specified for reference data lookup");
        }
        if (effectiveStreamKey.getStreamType() == null) {
            throw new ProcessException("No stream type has been specified for reference data lookup");
        }

        final NavigableSet<EffectiveMeta> effectiveStreams = cache.get(effectiveStreamKey);
        // Do this as trace as get() calls will be far more frequent than the create().
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(() -> LogUtil.message("get({}) - returned {} streams",
                    effectiveStreamKey, effectiveStreams.size()));
            logStreamSetToDebug(effectiveStreamKey, effectiveStreams, "after cache get");
        }
        return effectiveStreams;
    }

    protected NavigableSet<EffectiveMeta> create(final EffectiveStreamKey key) {
        return securityContext.asProcessingUserResult(() -> {
            NavigableSet<EffectiveMeta> effectiveStreamSet = Collections.emptyNavigableSet();

            try {
                LOGGER.debug(() -> "Creating effective stream set, key: " + LogUtil.toStringWithoutName(key));

                // Limit the stream set to the requested effective time window.
                final Period window = new Period(key.getFromMs(), key.getToMs());

                // Only find streams for the supplied feed and stream type.
                final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria(
                        window,
                        key.getFeed(),
                        key.getStreamType());

                LOGGER.debug(() -> "Using criteria: " + LogUtil.toStringWithoutName(criteria));

                // Hit the DB to find all the streams matching our criteria
                final List<EffectiveMeta> effectiveMetasFromDb = metaService.findEffectiveData(criteria);

                if (LOGGER.isDebugEnabled()) {
                    logStreamSetToDebug(key, effectiveMetasFromDb, "after DB fetch");
                }

                // Build them into a de-duped set with one stream per eff time
                effectiveStreamSet = buildEffectiveStreamSet(key, effectiveMetasFromDb);

                // Intern it so we don't have any duplicate sets in the cache
                effectiveStreamSet = internEffectiveStreamSet(key, effectiveStreamSet);
            } catch (final RuntimeException e) {
                LOGGER.error("Error creating effective stream set for " + key + " - " + e.getMessage(), e);
            }
            return effectiveStreamSet;
        });
    }

    private NavigableSet<EffectiveMeta> internEffectiveStreamSet(
            final EffectiveStreamKey key,
            final NavigableSet<EffectiveMeta> effectiveStreamSet) {

        // Intern the effective stream set, so we only have one identical
        // copy in memory.
        if (internPool != null) {
            // Could consider replacing with Guava's Interners.newWeakInterner()
            final NavigableSet<EffectiveMeta> internedSet = internPool.intern(effectiveStreamSet);

            if (LOGGER.isDebugEnabled()) {
                logStreamSetToDebug(key, internedSet, "after intern");
                validateInternResult(effectiveStreamSet, internedSet);
            }
            return internedSet;
        } else {
            return effectiveStreamSet;
        }
    }

    private static void logStreamSetToDebug(final EffectiveStreamKey key,
                                            final Collection<EffectiveMeta> effectiveStreams,
                                            final String msg) {
        if (effectiveStreams == null) {
            LOGGER.debug("Effective stream set is null, key: {}", key);
        } else {

            final String streamsStr = effectiveStreamsToString(effectiveStreams);

            LOGGER.debug("Effective streams ({}) of size {}, key: {}, streams:\n{}",
                    msg, effectiveStreams.size(), key, streamsStr);

            validateStreamSet(key, effectiveStreams, msg);
        }
    }

    private static String effectiveStreamsToString(final Collection<EffectiveMeta> effectiveStreams) {
        if (effectiveStreams == null) {
            return "";
        } else {
            final List<String> sortedStringMetas = effectiveStreams.stream()
                    .sorted(Comparator.comparing(EffectiveMeta::getEffectiveMs))
                    .map(LogUtil::toStringWithoutName)
                    .collect(Collectors.toList());

            Stream<String> stream = sortedStringMetas.stream();

            final int limit = 20;
            final int size = sortedStringMetas.size();
            if (size > limit) {
                final String lastMetaStr = sortedStringMetas.get(size - 1);

                stream = Stream.concat(
                        stream.limit(limit - 1),
                        Stream.of(
                                "...TRUNCATED...",
                                lastMetaStr));
            }
            return stream
                    .map(str -> "  " + str)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static void validateStreamSet(final EffectiveStreamKey key,
                                          final Collection<EffectiveMeta> effectiveStreamSet,
                                          final String msg) {

        LOGGER.debug("Validating effectiveStreamSet ({}) for key: {}", msg, key);

        // None of this should ever happen unless we have a problem somewhere

        final Set<String> invalidFeedNames = effectiveStreamSet.stream()
                .map(EffectiveMeta::getFeedName)
                .filter(feedName -> !Objects.equals(feedName, key.getFeed()))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!invalidFeedNames.isEmpty()) {
            LOGGER.error("Found {} incorrect feed names {} " +
                            "in effectiveStreamSet for key {}",
                    invalidFeedNames.size(), invalidFeedNames, key);
        }

        final Set<String> invalidTypes = effectiveStreamSet.stream()
                .map(EffectiveMeta::getTypeName)
                .filter(typeName -> !Objects.equals(typeName, key.getStreamType()))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!invalidTypes.isEmpty()) {
            LOGGER.error("Found {} incorrect stream type names {} " +
                            "in effectiveStreamSet for key {}",
                    invalidTypes.size(), invalidTypes, key);
        }

        final Set<EffectiveMeta> metasOutsideKeyWindow = effectiveStreamSet.stream()
                .filter(effectiveMeta -> !key.isTimeInKeyWindow(effectiveMeta.getEffectiveMs()))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!metasOutsideKeyWindow.isEmpty()) {
            LOGGER.error("Found {} effective streams with an effective time " +
                            "outside the key window. key: {}, invalid effective streams:\n{}",
                    metasOutsideKeyWindow.size(), key, effectiveStreamsToString(metasOutsideKeyWindow));
        }
    }

    private static void validateInternResult(final NavigableSet<EffectiveMeta> effectiveStreamSet,
                                             final NavigableSet<EffectiveMeta> internedSet) {
        // May not be the same instance, but should be equal
        if (!Objects.equals(internedSet, effectiveStreamSet)) {
            LOGGER.error("""
                            Set returned from the intern pool does not match input.\s
                            Input: {}:
                            {}
                            Output: {}:
                            {}""",
                    System.identityHashCode(effectiveStreamSet),
                    effectiveStreamSet,
                    System.identityHashCode(internedSet),
                    internedSet);
        }
    }

    private NavigableSet<EffectiveMeta> buildEffectiveStreamSet(final EffectiveStreamKey key,
                                                                final List<EffectiveMeta> effectiveMetasFromDb) {
        final NavigableSet<EffectiveMeta> effectiveStreamSet;
        if (NullSafe.isEmptyCollection(effectiveMetasFromDb)) {
            LOGGER.debug("No effective streams for key {}", key);
            effectiveStreamSet = Collections.emptyNavigableSet();
        } else {
            final Map<Long, List<EffectiveMeta>> map = effectiveMetasFromDb.stream()
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
                                        .sorted()
                                        .collect(Collectors.joining(", ")),
                                key.getFeed(),
                                Instant.ofEpochMilli(effectiveMs),
                                effectiveMeta.getId());
                    }

                    LOGGER.trace("Adding effective stream {} against key {}", effectiveMeta, key);
                    effectiveStreamSet.add(effectiveMeta);
                }
            });
        }
        if (LOGGER.isDebugEnabled()) {
            logStreamSetToDebug(key, effectiveStreamSet, "after de-dup");
        }
        return effectiveStreamSet;
    }

    long size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        final Comparator<Entry<EffectiveStreamKey, NavigableSet<EffectiveMeta>>> entryComparator =
                Comparator
                        .comparing((Entry<EffectiveStreamKey, NavigableSet<EffectiveMeta>> entry) ->
                                entry.getKey().getFeed())
                        .thenComparing(entry -> entry.getKey().getStreamType())
                        .thenComparing(entry -> entry.getKey().getFromMs());

        final List<Map<String, Object>> entries = cache.asMap()
                .entrySet()
                .stream()
                .sorted(entryComparator)
                .map(entry -> Map.of(
                        "effectiveStreamKey", LogUtil.toStringWithoutName(entry.getKey()),
                        "effectiveStreamSet", entry.getValue()
                                .stream()
                                .sorted()
                                .map(LogUtil::toStringWithoutName)
                                .collect(Collectors.toList()),
                        "effectiveStreamSetSize", entry.getValue().size()))
                .collect(Collectors.toList());

        return SystemInfoResult.builder(this)
                .description("Effective Stream Cache")
                .addDetail("internPoolSize", NullSafe.getOrElse(internPool, InternPool::size, -1))
                .addDetail("keyCount", cache.size())
                .addDetail("entries", entries)
                .build();
    }
}
