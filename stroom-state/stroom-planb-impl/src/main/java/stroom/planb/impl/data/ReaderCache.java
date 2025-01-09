package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.data.ShardCache.Shard;
import stroom.planb.impl.io.AbstractLmdbReader;
import stroom.planb.impl.io.RangedStateReader;
import stroom.planb.impl.io.SessionReader;
import stroom.planb.impl.io.StateReader;
import stroom.planb.impl.io.TemporalRangedStateReader;
import stroom.planb.impl.io.TemporalStateReader;
import stroom.planb.shared.PlanBDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class ReaderCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReaderCache.class);

    private static final String CACHE_NAME = "PlanB Reader Cache";

    private final LoadingStroomCache<String, Optional<AbstractLmdbReader<?, ?>>> cache;
    private final ShardCache shardCache;
    private final ByteBufferFactory byteBufferFactory;

    @Inject
    public ReaderCache(final Provider<PlanBConfig> configProvider,
                       final CacheManager cacheManager,
                       final ShardCache shardCache,
                       final ByteBufferFactory byteBufferFactory) {
        this.shardCache = shardCache;
        this.byteBufferFactory = byteBufferFactory;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> configProvider.get().getReaderCache(),
                this::create,
                this::destroy);
    }

    public Optional<AbstractLmdbReader<?, ?>> get(final String mapName) {
        return cache.get(mapName);
    }

    private Optional<AbstractLmdbReader<?, ?>> create(final String mapName) {
        try {
            final Optional<Shard> optionalShard = shardCache.get(mapName);
            return optionalShard.map(shard -> createReader(shard.path(), shard.doc()));
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
        return Optional.empty();
    }

    private void destroy(final String mapName,
                         final Optional<AbstractLmdbReader<?, ?>> optional) {
        optional.ifPresent(AbstractLmdbReader::close);
    }

    private AbstractLmdbReader<?, ?> createReader(final Path path,
                                                  final PlanBDoc doc) {

        switch (doc.getStateType()) {
            case STATE -> {
                return new StateReader(path, byteBufferFactory);
            }
            case TEMPORAL_STATE -> {
                return new TemporalStateReader(path, byteBufferFactory);
            }
            case RANGED_STATE -> {
                return new RangedStateReader(path, byteBufferFactory);
            }
            case TEMPORAL_RANGED_STATE -> {
                return new TemporalRangedStateReader(path, byteBufferFactory);
            }
            case SESSION -> {
                return new SessionReader(path, byteBufferFactory);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
