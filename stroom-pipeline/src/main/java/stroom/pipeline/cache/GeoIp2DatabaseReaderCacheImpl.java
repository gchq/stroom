package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Store;
import stroom.meta.api.EffectiveMeta;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.refdata.EffectiveStreamCache;
import stroom.pipeline.refdata.EffectiveStreamKey;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.maxmind.geoip2.DatabaseReader;

import java.io.IOException;
import java.time.Instant;
import java.util.NavigableSet;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class GeoIp2DatabaseReaderCacheImpl implements GeoIp2DatabaseReaderCache, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GeoIp2DatabaseReaderCacheImpl.class);
    private static final String CACHE_NAME = "Maxmind GeoIP2 Database Reader Cache";

    private final LoadingStroomCache<EffectiveStreamKey, DatabaseReader> cache;
    private final EffectiveStreamCache effectiveStreamCache;
    private final Store streamStore;
    private final SecurityContext securityContext;

    @Inject
    GeoIp2DatabaseReaderCacheImpl(final CacheManager cacheManager,
                                  final EffectiveStreamCache effectiveStreamCache,
                                  final Provider<PipelineConfig> pipelineConfigProvider,
                                  final Store streamStore,
                                  final SecurityContext securityContext) {
        this.effectiveStreamCache = effectiveStreamCache;
        this.streamStore = streamStore;
        this.securityContext = securityContext;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getGeoIp2DatabaseReaderCache(),
                this::createReader,
                this::closeReader);
    }

    private DatabaseReader createReader(final EffectiveStreamKey key) {
        return securityContext.asProcessingUserResult(() -> {
            try {
                LOGGER.debug(() -> "Creating GeoIp2 database reader key: " + LogUtil.toStringWithoutClassName(key));

                final NavigableSet<EffectiveMeta> effectiveStreams = effectiveStreamCache.get(key);
                if (effectiveStreams != null && !effectiveStreams.isEmpty()) {
                    EffectiveMeta effectiveStream = EffectiveMeta.findLatestBefore(key.getToMs(), effectiveStreams);
                    LOGGER.debug("Effective stream found. Feed: {}, stream ID: {}",
                            effectiveStream.getFeedName(), effectiveStream.getId());

                    final SegmentInputStream metaStream = streamStore.openSource(effectiveStream.getId()).get(0).get();

                    return new DatabaseReader.Builder(metaStream).build();
                } else {
                    LOGGER.error("No effective stream found. Key: {}", key);
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.error("Error creating GeoIp2 database reader for " + key + " - " + e.getMessage(), e);
            }

            return null;
        });
    }

    @Override
    public DatabaseReader getReader(final String feedName, final String streamType, final Instant time) {
        final EffectiveStreamKey key = EffectiveStreamKey.forLookupTime(feedName, streamType, time.toEpochMilli());
        return cache.get(key);
    }

    private void closeReader(final EffectiveStreamKey key, final DatabaseReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close Maxmind GeoIp2 DatabaseReader", e);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
