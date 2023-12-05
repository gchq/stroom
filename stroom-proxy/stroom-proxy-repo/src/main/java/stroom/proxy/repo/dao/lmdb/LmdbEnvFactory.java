package stroom.proxy.repo.dao.lmdb;

import stroom.lmdb.LmdbConfig;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Env;
import org.lmdbjava.Env.Builder;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

public class LmdbEnvFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvFactory.class);

    public Env<ByteBuffer> build(final PathCreator pathCreator,
                                 final LmdbConfig lmdbConfig) {
        final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);
        final boolean isReadAheadEnabled = lmdbConfig.isReadAheadEnabled();
        final Path envDir = pathCreator.toAppPath(lmdbConfig.getLocalDir());

        LOGGER.debug(() -> "Ensuring existence of directory " + envDir.toAbsolutePath().normalize());
        try {
            Files.createDirectories(envDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error creating directory {}: {}", envDir.toAbsolutePath().normalize(), e));
        }

        final Env<ByteBuffer> env;
        try {
            final Builder<ByteBuffer> builder = Env.create()
                    .setMapSize(lmdbConfig.getMaxStoreSize().getBytes())
                    .setMaxDbs(100)
                    .setMaxReaders(126);

            if (envFlags.contains(EnvFlags.MDB_NORDAHEAD) && isReadAheadEnabled) {
                throw new RuntimeException("Can't set isReadAheadEnabled to true and add flag "
                        + EnvFlags.MDB_NORDAHEAD);
            }

            envFlags.add(EnvFlags.MDB_NOTLS);
            if (!isReadAheadEnabled) {
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
        return env;
    }
}
