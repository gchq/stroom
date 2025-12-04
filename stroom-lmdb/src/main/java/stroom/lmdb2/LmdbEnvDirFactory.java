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

package stroom.lmdb2;

import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbLibrary;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Singleton
public class LmdbEnvDirFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvDirFactory.class);

    private final PathCreator pathCreator;

    @Inject
    public LmdbEnvDirFactory(final LmdbLibrary lmdbLib,
                             final PathCreator pathCreator) {

        // Library config is done via java system props and is static code in LMDBJava so
        // only want to do it once
        lmdbLib.init();

        this.pathCreator = pathCreator;
    }

    public Builder builder() {
        return new Builder(pathCreator);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final PathCreator pathCreator;
        private Path localDir;
        private String subDir;

        private Builder(final PathCreator pathCreator) {
            this.pathCreator = pathCreator;
        }

        public Builder config(final LmdbConfig lmdbConfig) {
            this.localDir = getLocalDirAsPath(pathCreator, lmdbConfig);
            return this;
        }

        public Builder localDir(final Path localDir) {
            this.localDir = localDir;
            return this;
        }

        public Builder subDir(final String subDir) {
            this.subDir = subDir;
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

        public LmdbEnvDir build() {

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

            return new LmdbEnvDir(envDir, isDedicatedDir);
        }
    }
}
