package stroom.lmdb2;

import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbLibrary;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Singleton
public class LmdbEnvFactory2 {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvFactory2.class);

    private final PathCreator pathCreator;

    @Inject
    public LmdbEnvFactory2(final LmdbLibrary lmdbLib,
                           final PathCreator pathCreator) {
        this.pathCreator = pathCreator;

        // Library config is done via java system props and is static code in LMDBJava so
        // only want to do it once
        lmdbLib.init();
    }

    public Builder builder() {
        return new Builder(pathCreator);
    }

    public static class Builder {

        private final PathCreator pathCreator;
        private Path localDir;
        private final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);

        protected int maxReaders = LmdbConfig.DEFAULT_MAX_READERS;
        protected ByteSize maxStoreSize = LmdbConfig.DEFAULT_MAX_STORE_SIZE;
        protected int maxDbs = 1;
        protected boolean isReadAheadEnabled = LmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED;
        protected boolean isReaderBlockedByWriter = LmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER;
        protected String subDir = null;
        protected String name = null;

        private Builder(final PathCreator pathCreator) {
            this.pathCreator = pathCreator;
        }

        public Builder config(final LmdbConfig lmdbConfig) {
            this.localDir = getLocalDirAsPath(pathCreator, lmdbConfig);
            this.maxReaders = lmdbConfig.getMaxReaders();
            this.maxStoreSize = lmdbConfig.getMaxStoreSize();
            this.isReadAheadEnabled = lmdbConfig.isReadAheadEnabled();
            this.isReaderBlockedByWriter = lmdbConfig.isReaderBlockedByWriter();
            return this;
        }

        public Builder localDir(final Path localDir) {
            this.localDir = localDir;
            return this;
        }

        public Builder maxDbCount(final int maxDbCount) {
            this.maxDbs = maxDbCount;
            return this;
        }

        public Builder addEnvFlag(final EnvFlags envFlag) {
            this.envFlags.add(envFlag);
            return this;
        }

        public Builder envFlags(final EnvFlags... envFlags) {
            this.envFlags.addAll(Arrays.asList(envFlags));
            return this;
        }

        public Builder envFlags(final Collection<EnvFlags> envFlags) {
            this.envFlags.addAll(envFlags);
            return this;
        }

        public Builder subDir(final String subDir) {
            this.subDir = subDir;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        private static Path getLocalDirAsPath(final PathCreator pathCreator,
                                              final LmdbConfig lmdbConfig) {

            Objects.requireNonNull(lmdbConfig.getLocalDir());

            final Path localDir = pathCreator.toAppPath(lmdbConfig.getLocalDir());

            try {
                LOGGER.debug(() -> LogUtil.message("Ensuring directory {} exists (from configuration property {})",
                        localDir.toAbsolutePath(),
                        lmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME)));
                Files.createDirectories(localDir);
            } catch (IOException e) {
                throw new RuntimeException(
                        LogUtil.message("Error ensuring directory {} exists (from configuration property {})",
                                localDir.toAbsolutePath(),
                                lmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME)), e);
            }

            if (!Files.isReadable(localDir)) {
                throw new RuntimeException(
                        LogUtil.message("Directory {} (from configuration property {}) is not readable",
                                localDir.toAbsolutePath(),
                                lmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME)));
            }

            if (!Files.isWritable(localDir)) {
                throw new RuntimeException(
                        LogUtil.message("Directory {} (from configuration property {}) is not writable",
                                localDir.toAbsolutePath(),
                                lmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME)));
            }

            return localDir;
        }

        public LmdbEnv2 build() {

            final Path envDir;
            final boolean isDedicatedDir;
            if (subDir != null && !subDir.isBlank()) {
                envDir = localDir.resolve(subDir);
                isDedicatedDir = true;

                LOGGER.debug(() -> "Ensuring existence of directory " + envDir.toAbsolutePath().normalize());
                try {
                    Files.createDirectories(envDir);
                } catch (IOException e) {
                    throw new RuntimeException(LogUtil.message(
                            "Error creating directory {}: {}", envDir.toAbsolutePath().normalize(), e));
                }
            } else {
                envDir = localDir;
                isDedicatedDir = false;
            }

            final Env<ByteBuffer> env;
            try {
                final Env.Builder<ByteBuffer> builder = Env.create()
                        .setMapSize(maxStoreSize.getBytes())
                        .setMaxDbs(maxDbs)
                        .setMaxReaders(maxReaders);

                if (envFlags.contains(EnvFlags.MDB_NORDAHEAD) && isReadAheadEnabled) {
                    throw new RuntimeException("Can't set isReadAheadEnabled to true and add flag "
                            + EnvFlags.MDB_NORDAHEAD);
                }

                envFlags.add(EnvFlags.MDB_NOTLS);
                if (!isReadAheadEnabled) {
                    envFlags.add(EnvFlags.MDB_NORDAHEAD);
                }

                LOGGER.debug("Creating LMDB environment in dir {}, maxSize: {}, maxDbs {}, maxReaders {}, "
                                + "isReadAheadEnabled {}, isReaderBlockedByWriter {}, envFlags {}",
                        envDir.toAbsolutePath().normalize(),
                        maxStoreSize,
                        maxDbs,
                        maxReaders,
                        isReadAheadEnabled,
                        isReaderBlockedByWriter,
                        envFlags);

                final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);
                env = builder.open(envDir.toFile(), envFlagsArr);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error creating LMDB env at {}: {}",
                        envDir.toAbsolutePath().normalize(), e.getMessage()), e);
            }
            return new LmdbEnv2(env, envDir, isDedicatedDir, name, envFlags);
        }
    }
}
