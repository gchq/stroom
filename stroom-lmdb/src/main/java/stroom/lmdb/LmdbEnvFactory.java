/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.lmdb;

import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Env;
import org.lmdbjava.Env.Builder;
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

@Singleton // The LMDB lib is dealt with statically by LMDB java so only want to initialise it once
public class LmdbEnvFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvFactory.class);

    private final PathCreator pathCreator;

    @Inject
    public LmdbEnvFactory(final PathCreator pathCreator,
                          final LmdbLibrary lmdbLib) {
        this.pathCreator = pathCreator;

        // Library config is done via java system props and is static code in LMDBJava so
        // only want to do it once
        lmdbLib.init();
    }

    /**
     * Create an LMDB env builder from {@link LmdbConfig} which can then be further configured.
     * For a fully custom builder use one of the other builder methods.
     */
    public SimpleEnvBuilder builder(final LmdbConfig lmdbConfig) {
        return new SimpleEnvBuilder(pathCreator, lmdbConfig);
    }

    /**
     * @param dir The path where the environment will be located on the filesystem.
     *            Should be local disk and not shared storage. You can have multiple LMDB
     *            environments in this dir if you call {@link CustomEnvBuilder#subDir} with
     *            a dedicated subDir for each env.
     */
    public CustomEnvBuilder builder(final Path dir) {
        return new CustomEnvBuilder(dir);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    abstract static class AbstractEnvBuilder {

        private final Path localDir;
        private final Set<EnvFlags> envFlags = EnumSet.noneOf(EnvFlags.class);

        protected int maxReaders = LmdbConfig.DEFAULT_MAX_READERS;
        protected ByteSize maxStoreSize = LmdbConfig.DEFAULT_MAX_STORE_SIZE;
        protected int maxDbs = 1;
        protected boolean isReadAheadEnabled = LmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED;
        protected boolean isReaderBlockedByWriter = LmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER;
        protected String subDir = null;
        protected String name = null;

        private AbstractEnvBuilder(final PathCreator pathCreator,
                                   final LmdbConfig lmdbConfig) {
            this(getLocalDirAsPath(pathCreator, lmdbConfig));

            this.maxReaders = lmdbConfig.getMaxReaders();
            this.maxStoreSize = lmdbConfig.getMaxStoreSize();
            this.isReadAheadEnabled = lmdbConfig.isReadAheadEnabled();
            this.isReaderBlockedByWriter = lmdbConfig.isReaderBlockedByWriter();
        }

        private AbstractEnvBuilder(final Path localDir) {
            this.localDir = localDir;
        }

        public AbstractEnvBuilder withMaxDbCount(final int maxDbCount) {
            this.maxDbs = maxDbCount;
            return this;
        }

        public AbstractEnvBuilder addEnvFlag(final EnvFlags envFlag) {
            this.envFlags.add(envFlag);
            return this;
        }

        public AbstractEnvBuilder withEnvFlags(final EnvFlags... envFlags) {
            this.envFlags.addAll(Arrays.asList(envFlags));
            return this;
        }

        public AbstractEnvBuilder withEnvFlags(final Collection<EnvFlags> envFlags) {
            this.envFlags.addAll(envFlags);
            return this;
        }

        public AbstractEnvBuilder withSubDirectory(final String subDir) {
            this.subDir = subDir;
            return this;
        }

        public AbstractEnvBuilder withName(final String name) {
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
            } catch (final IOException e) {
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

        public LmdbEnv build() {

            final Path envDir;
            final boolean isDedicatedDir;
            if (subDir != null && !subDir.isBlank()) {
                envDir = localDir.resolve(subDir);
                isDedicatedDir = true;

                LOGGER.debug(() -> "Ensuring existence of directory " + envDir.toAbsolutePath().normalize());
                try {
                    Files.createDirectories(envDir);
                } catch (final IOException e) {
                    throw new RuntimeException(LogUtil.message(
                            "Error creating directory {}: {}", envDir.toAbsolutePath().normalize(), e));
                }
            } else {
                envDir = localDir;
                isDedicatedDir = false;
            }

            final Env<ByteBuffer> env;
            try {
                final Builder<ByteBuffer> builder = Env.create()
                        .setMapSize(maxStoreSize.getBytes())
                        .setMaxDbs(maxDbs)
                        .setMaxReaders(maxReaders);

                if (envFlags.contains(EnvFlags.MDB_NORDAHEAD) && isReadAheadEnabled) {
                    throw new RuntimeException("Can't set isReadAheadEnabled to true and add flag "
                            + EnvFlags.MDB_NORDAHEAD);
                }

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
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error creating LMDB env at {}: {}",
                        envDir.toAbsolutePath().normalize(), e.getMessage()), e);
            }
            return new LmdbEnv(envDir, name, env, envFlags, isReaderBlockedByWriter, isDedicatedDir);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * A builder for when you don't have an {@link LmdbConfig}
     */
    public static class CustomEnvBuilder extends AbstractEnvBuilder {

        private CustomEnvBuilder(final Path dir) {
            super(dir);
        }

        @Override
        public CustomEnvBuilder withMaxDbCount(final int maxDbCount) {
            this.maxDbs = maxDbCount;
            return this;
        }

        public CustomEnvBuilder withMapSize(final ByteSize byteSize) {
            this.maxStoreSize = byteSize;
            return this;
        }

        public CustomEnvBuilder withMaxReaderCount(final int maxReaderCount) {
            this.maxReaders = maxReaderCount;
            return this;
        }

        @Override
        public CustomEnvBuilder withSubDirectory(final String subDirectory) {
            super.withSubDirectory(subDirectory);
            return this;
        }

        @Override
        public CustomEnvBuilder withName(final String name) {
            super.withName(name);
            return this;
        }

        @Override
        public CustomEnvBuilder addEnvFlag(final EnvFlags envFlag) {
            super.addEnvFlag(envFlag);
            return this;
        }

        @Override
        public CustomEnvBuilder withEnvFlags(final EnvFlags... envFlags) {
            super.withEnvFlags(envFlags);
            return this;
        }

        @Override
        public CustomEnvBuilder withEnvFlags(final Collection<EnvFlags> envFlags) {
            super.withEnvFlags(envFlags);
            return this;
        }

        public CustomEnvBuilder makeWritersBlockReaders() {
            super.isReaderBlockedByWriter = true;
            return this;
        }

        public CustomEnvBuilder setIsReaderBlockedByWriter(final boolean isReaderBlockedByWriter) {
            super.isReaderBlockedByWriter = isReaderBlockedByWriter;
            return this;
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * A builder for when you have an {@link LmdbConfig}
     */
    public static class SimpleEnvBuilder extends AbstractEnvBuilder {

        private SimpleEnvBuilder(final PathCreator pathCreator,
                                 final LmdbConfig lmdbConfig) {
            super(pathCreator, lmdbConfig);
        }

        @Override
        public SimpleEnvBuilder withMaxDbCount(final int maxDbCount) {
            this.maxDbs = maxDbCount;
            return this;
        }

        @Override
        public SimpleEnvBuilder addEnvFlag(final EnvFlags envFlag) {
            super.addEnvFlag(envFlag);
            return this;
        }

        @Override
        public SimpleEnvBuilder withEnvFlags(final EnvFlags... envFlags) {
            super.withEnvFlags(envFlags);
            return this;
        }

        @Override
        public SimpleEnvBuilder withEnvFlags(final Collection<EnvFlags> envFlags) {
            super.withEnvFlags(envFlags);
            return this;
        }

        @Override
        public SimpleEnvBuilder withSubDirectory(final String subDirectory) {
            super.withSubDirectory(subDirectory);
            return this;
        }

        @Override
        public SimpleEnvBuilder withName(final String name) {
            super.withName(name);
            return this;
        }
    }
}
