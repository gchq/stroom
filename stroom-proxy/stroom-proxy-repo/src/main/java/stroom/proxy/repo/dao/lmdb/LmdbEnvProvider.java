package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.lmdbjava.Env;
import org.lmdbjava.Env.Builder;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

@Singleton
public class LmdbEnvProvider implements Provider<LmdbEnv> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvProvider.class);

    private final LmdbEnv lmdbEnv;
    private final ByteBufferPool byteBufferPool;

    @Inject
    public LmdbEnvProvider(final ProxyLmdbConfig lmdbConfig,
                           final PathCreator pathCreator,
                           final ByteBufferPool byteBufferPool) {
        this.byteBufferPool = byteBufferPool;
        final Path envDir = pathCreator.toAppPath(lmdbConfig.getLocalDir());
        LOGGER.debug(() -> "Ensuring existence of directory " + envDir.toAbsolutePath().normalize());
        try {
            Files.createDirectories(envDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error creating directory {}: {}", envDir.toAbsolutePath().normalize(), e));
        }

        this.lmdbEnv = create(envDir, lmdbConfig);
        lmdbEnv.setAutoCommit(new AutoCommit(10000, Duration.ofSeconds(10)));
    }

    public LmdbEnv create(final Path envDir, final ProxyLmdbConfig lmdbConfig) {
        final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);
        final Env<ByteBuffer> env;
        try {
            final Builder<ByteBuffer> builder = Env.create()
                    .setMapSize(lmdbConfig.getMaxStoreSize().getBytes())
                    .setMaxDbs(100)
                    .setMaxReaders(126);

            if (envFlags.contains(EnvFlags.MDB_NORDAHEAD) && lmdbConfig.isReadAheadEnabled()) {
                throw new RuntimeException("Can't set isReadAheadEnabled to true and add flag "
                        + EnvFlags.MDB_NORDAHEAD);
            }

            envFlags.add(EnvFlags.MDB_NOTLS);
            if (!lmdbConfig.isReadAheadEnabled()) {
                envFlags.add(EnvFlags.MDB_NORDAHEAD);
            }

//            LOGGER.debug("Creating LMDB environment in dir {}, maxSize: {}, maxDbs {}, maxReaders {}, "
//                            + "isReadAheadEnabled {}, isReaderBlockedByWriter {}, envFlags {}",
//                    envDir.toAbsolutePath().normalize(),
//                    maxStoreSize,
//                    maxDbs,
//                    maxReaders,
//                    isReadAheadEnabled,
//                    isReaderBlockedByWriter,
//                    envFlags);

            final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);
            env = builder.open(envDir.toFile(), envFlagsArr);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error creating LMDB env at {}: {}",
                    envDir.toAbsolutePath().normalize(), e.getMessage()), e);
        }
        return new LmdbEnv(env, byteBufferPool);
    }

    @Override
    public LmdbEnv get() {
        return lmdbEnv;
    }
}
