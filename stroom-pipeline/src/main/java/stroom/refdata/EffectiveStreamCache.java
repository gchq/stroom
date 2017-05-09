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

import net.sf.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.entity.shared.Period;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.streamstore.server.EffectiveMetaDataCriteria;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;

import javax.inject.Inject;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

@Component
public class EffectiveStreamCache extends AbstractCacheBean<EffectiveStreamKey, TreeSet<EffectiveStream>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectiveStreamCache.class);

    // = 86 400 000
    public static final long ONE_DAY = 1000 * 60 * 60 * 24;
    // round up one day to 100000000
    public static final long APPROX_DAY = 100000000;
    // actually 11.5 days but this is fine for the purposes of reference data.
    public static final long APPROX_TEN_DAYS = 1000000000;

    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final StreamStore streamStore;
    private final EffectiveStreamInternPool internPool;

    @Inject
    public EffectiveStreamCache(final CacheManager cacheManager, final StreamStore streamStore,
            final EffectiveStreamInternPool internPool) {
        super(cacheManager, "Reference Data - Effective Stream Cache", MAX_CACHE_ENTRIES);
        this.streamStore = streamStore;
        this.internPool = internPool;
        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public TreeSet<EffectiveStream> get(final EffectiveStreamKey effectiveStreamKey) {
        if (effectiveStreamKey.getFeed() == null) {
            throw new ProcessException("No feed has been specified for reference data lookup");
        }
        if (effectiveStreamKey.getStreamType() == null) {
            throw new ProcessException("No stream type has been specified for reference data lookup");
        }

        return super.get(effectiveStreamKey);
    }

    @Override
    protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
        TreeSet<EffectiveStream> effectiveStreamSet = null;

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
                    EffectiveStream effectiveStream = null;

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
            LOGGER.error("Unable to create stream cache!", e);
        }

        // Make sure this pool always returns some kind of effective stream set
        // even if an exception was thrown during load.
        if (effectiveStreamSet == null) {
            effectiveStreamSet = new TreeSet<>();
        }

        return effectiveStreamSet;
    }

    /**
     * Gets a time less than the supplied time, rounded down to the nearest 11.5
     * days (one billion milliseconds).
     *
     * @param time
     * @return
     */
    public long getBaseTime(final long time) {
        final long multiple = time / APPROX_TEN_DAYS;
        final long periodStart = multiple * APPROX_TEN_DAYS;
        return periodStart;
    }
}
