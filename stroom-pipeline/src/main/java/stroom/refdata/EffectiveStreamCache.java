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

package stroom.refdata;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Period;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.streamstore.server.EffectiveMetaDataCriteria;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public class EffectiveStreamCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectiveStreamCache.class);

    //    // = 86 400 000
//    private static final long ONE_DAY = 1000 * 60 * 60 * 24;
//    // round up one day to 100000000
//    private static final long APPROX_DAY = 100000000;
    // actually 11.5 days but this is fine for the purposes of reference data.
    private static final long APPROX_TEN_DAYS = 1000000000;

    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<EffectiveStreamKey, NavigableSet> cache;
    private final StreamStore streamStore;
    private final EffectiveStreamInternPool internPool;
    private final SecurityContext securityContext;

    @Inject
    @SuppressWarnings("unchecked")
    EffectiveStreamCache(final CacheManager cacheManager,
                         final StreamStore streamStore,
                         final EffectiveStreamInternPool internPool,
                         final SecurityContext securityContext) {
        this.streamStore = streamStore;
        this.internPool = internPool;
        this.securityContext = securityContext;

        final CacheLoader<EffectiveStreamKey, NavigableSet> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Reference Data - Effective Stream Cache", cacheBuilder, cache);
    }

    @SuppressWarnings("unchecked")
    public NavigableSet<EffectiveStream> get(final EffectiveStreamKey effectiveStreamKey) {
        if (effectiveStreamKey.getFeed() == null) {
            throw new ProcessException("No feed has been specified for reference data lookup");
        }
        if (effectiveStreamKey.getStreamType() == null) {
            throw new ProcessException("No stream type has been specified for reference data lookup");
        }

        return cache.getUnchecked(effectiveStreamKey);
    }

    protected NavigableSet<EffectiveStream> create(final EffectiveStreamKey key) {
        try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
            NavigableSet<EffectiveStream> effectiveStreamSet = Collections.emptyNavigableSet();

            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating effective time set: " + key.toString());
                }

                // Only find streams for the supplied feed and stream type.
                final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria();
                criteria.setFeed(key.getFeed());
                criteria.setStreamType(key.getStreamType());

                // Limit the stream set to the day starting from the supplied
                // effective time.
                final long effectiveMs = key.getEffectiveMs();
                // final Period window = new Period(effectiveMs, effectiveMs +
                // ONE_DAY);
                final Period window = new Period(effectiveMs, effectiveMs + APPROX_TEN_DAYS);
                criteria.setEffectivePeriod(window);

                // Locate all streams that fit the supplied criteria.
                final List<Stream> streams = streamStore.findEffectiveStream(criteria);

                // Add all streams that we have found to the effective stream set.
                if (streams != null && streams.size() > 0) {
                    effectiveStreamSet = new TreeSet<>();
                    for (final Stream stream : streams) {
                        EffectiveStream effectiveStream;

                        if (stream.getEffectiveMs() != null) {
                            effectiveStream = new EffectiveStream(stream.getId(), stream.getEffectiveMs());
                        } else {
                            effectiveStream = new EffectiveStream(stream.getId(), stream.getCreateMs());
                        }

                        final boolean success = effectiveStreamSet.add(effectiveStream);

                        // Warn if there are more than one effective stream for
                        // exactly the same time.
                        if (!success) {
                            LOGGER.warn("Attempt to insert effective stream with id=" + effectiveStream.getStreamId()
                                    + ". Duplicate match found with effectiveMs=" + effectiveStream.getEffectiveMs());
                        }
                    }
                }

                // Intern the effective stream set so we only have one identical
                // copy in memory.
                if (internPool != null) {
                    effectiveStreamSet = internPool.intern(effectiveStreamSet);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Created effective stream set: " + key.toString());
                }
            } catch (final Throwable e) {
                LOGGER.error(e.getMessage(), e);
            }

            return effectiveStreamSet;
        }
    }

    /**
     * Gets a time less than the supplied time, rounded down to the nearest 11.5
     * days (one billion milliseconds).
     */
    long getBaseTime(final long time) {
        final long multiple = time / APPROX_TEN_DAYS;
        return multiple * APPROX_TEN_DAYS;
    }

    long size() {
        return cache.size();
    }
}
