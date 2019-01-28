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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Period;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.security.Security;
import stroom.meta.shared.EffectiveMetaDataCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaService;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

@Singleton
public class EffectiveStreamCache implements Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectiveStreamCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<EffectiveStreamKey, NavigableSet> cache;
    private final MetaService metaService;
    private final EffectiveStreamInternPool internPool;
    private final Security security;

    @Inject
    EffectiveStreamCache(final CacheManager cacheManager,
                         final MetaService metaService,
                         final EffectiveStreamInternPool internPool,
                         final Security security) {
        this(cacheManager, metaService, internPool, security, 10, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    EffectiveStreamCache(final CacheManager cacheManager,
                         final MetaService metaService,
                         final EffectiveStreamInternPool internPool,
                         final Security security,
                         final long duration,
                         final TimeUnit unit) {
        this.metaService = metaService;
        this.internPool = internPool;
        this.security = security;

        final CacheLoader<EffectiveStreamKey, NavigableSet> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(duration, unit);
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
        return security.asProcessingUserResult(() -> {
            NavigableSet<EffectiveStream> effectiveStreamSet = Collections.emptyNavigableSet();

            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating effective time set: " + key.toString());
                }

                // Only find streams for the supplied feed and stream type.
                final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria();
                criteria.setFeed(key.getFeed());
                criteria.setType(key.getStreamType());

                // Limit the stream set to the requested effective time window.
                final Period window = new Period(key.getFromMs(), key.getToMs());
                criteria.setEffectivePeriod(window);

                // Locate all streams that fit the supplied criteria.
                final Set<Meta> streams = metaService.findEffectiveData(criteria);

                // Add all streams that we have found to the effective stream set.
                if (streams != null && streams.size() > 0) {
                    effectiveStreamSet = new TreeSet<>();
                    for (final Meta meta : streams) {
                        EffectiveStream effectiveStream;

                        if (meta.getEffectiveMs() != null) {
                            effectiveStream = new EffectiveStream(meta.getId(), meta.getEffectiveMs());
                        } else {
                            effectiveStream = new EffectiveStream(meta.getId(), meta.getCreateMs());
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
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return effectiveStreamSet;
        });
    }

    long size() {
        return cache.size();
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}
