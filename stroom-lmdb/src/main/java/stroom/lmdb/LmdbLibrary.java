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

import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@Singleton // The LMDB lib is dealt with statically by LMDB java so only want to initialise it once
public class LmdbLibrary {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbLibrary.class);
    private static volatile boolean HAS_LIBRARY_BEEN_CONFIGURED = false;

    private final PathCreator pathCreator;
    private final TempDirProvider tempDirProvider;
    private final Provider<LmdbLibraryConfig> lmdbLibraryConfigProvider;

    @Inject
    public LmdbLibrary(final PathCreator pathCreator,
                       final TempDirProvider tempDirProvider,
                       final Provider<LmdbLibraryConfig> lmdbLibraryConfigProvider) {
        this.pathCreator = pathCreator;
        this.tempDirProvider = tempDirProvider;
        this.lmdbLibraryConfigProvider = lmdbLibraryConfigProvider;
    }

    public void init() {
        // Library config is done via java system props and is static code in LMDBJava so
        // only want to do it once
        if (!HAS_LIBRARY_BEEN_CONFIGURED) {
            configureLibraryUnderLock(lmdbLibraryConfigProvider.get());
        }
    }

    /**
     * Relies on this class being a singleton
     */
    private synchronized void configureLibraryUnderLock(final LmdbLibraryConfig lmdbLibraryConfig) {
        if (!HAS_LIBRARY_BEEN_CONFIGURED) {
            LOGGER.info("Configuring LMDB system library");

            final Path lmdbSystemLibraryPath = Optional.ofNullable(lmdbLibraryConfig.getProvidedSystemLibraryPath())
                    .map(pathCreator::toAppPath)
                    .orElse(null);

            if (lmdbSystemLibraryPath != null) {
                if (!Files.isReadable(lmdbSystemLibraryPath)) {
                    throw new RuntimeException("Unable to read LMDB system library at " +
                            lmdbSystemLibraryPath.toAbsolutePath().normalize());
                }
                // jakarta.validation should ensure the path is valid if set
                final String lmdbNativeLibProp = LmdbLibraryConfig.LMDB_NATIVE_LIB_PROP;
                System.setProperty(lmdbNativeLibProp, lmdbSystemLibraryPath.toAbsolutePath().normalize().toString());
                LOGGER.info("Using provided LMDB system library file. Setting prop {} to '{}'",
                        lmdbNativeLibProp, lmdbSystemLibraryPath);
            } else {
                final Path systemLibraryExtractDir = getLibraryExtractDir(lmdbLibraryConfig);

                // LMDB extracts the lib on boot to a unique temp file and should delete on JVM exit,
                // but just in case clear out any old ones.
                cleanUpExtractDir(systemLibraryExtractDir);

                // Set the location to extract the bundled LMDB binary to
                final String lmdbExtractDirProp = LmdbLibraryConfig.LMDB_EXTRACT_DIR_PROP;
                System.setProperty(
                        lmdbExtractDirProp,
                        systemLibraryExtractDir.toAbsolutePath().normalize().toString());
                LOGGER.info("Bundled LMDB system library binary will be extracted. Setting prop {} to '{}'",
                        lmdbExtractDirProp, systemLibraryExtractDir);
                HAS_LIBRARY_BEEN_CONFIGURED = true;
            }
        } else {
            LOGGER.debug("Another thread beat us to it");
        }
    }

    private Path getLibraryExtractDir(final LmdbLibraryConfig lmdbLibraryConfig) {
        final String extractDirStr = lmdbLibraryConfig.getSystemLibraryExtractDir();

        final String extractDirPropName = lmdbLibraryConfig.getFullPathStr(LmdbLibraryConfig.EXTRACT_DIR_PROP_NAME);

        Path extractDir;
        if (extractDirStr == null) {
            LOGGER.warn("LMDB system library extract dir is not set ({}), falling back to temporary directory {}.",
                    extractDirPropName,
                    tempDirProvider.get());

            extractDir = tempDirProvider.get();
            Objects.requireNonNull(extractDir, "Temp dir is not set");
            extractDir = extractDir.resolve(LmdbLibraryConfig.DEFAULT_LIBRARY_EXTRACT_SUB_DIR_NAME);
        } else {
            extractDir = pathCreator.toAppPath(extractDirStr);
        }

        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Ensuring directory {} exists (from configuration property {})",
                        extractDir.toAbsolutePath(),
                        extractDirPropName);
            }
            Files.createDirectories(extractDir);
        } catch (final IOException e) {
            throw new RuntimeException(
                    LogUtil.message("Error ensuring directory {} exists (from configuration property {})",
                            extractDir.toAbsolutePath(),
                            extractDirPropName), e);
        }

        if (!Files.isReadable(extractDir)) {
            throw new RuntimeException(
                    LogUtil.message("Directory {} (from configuration property {}) is not readable",
                            extractDir.toAbsolutePath(),
                            extractDirPropName));
        }

        if (!Files.isWritable(extractDir)) {
            throw new RuntimeException(
                    LogUtil.message("Directory {} (from configuration property {}) is not writable",
                            extractDir.toAbsolutePath(),
                            extractDirPropName));
        }

        return extractDir;
    }

    private void cleanUpExtractDir(final Path extractDir) {
        try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(
                extractDir, "lmdbjava-native-library-*.so")) {

            for (final Path redundantLibraryFilePath : directoryStream) {
                LOGGER.info("Deleting redundant LMDB library file "
                        + redundantLibraryFilePath.toAbsolutePath().normalize());
                try {
                    Files.deleteIfExists(redundantLibraryFilePath);
                } catch (final IOException e) {
                    LOGGER.error("Unable to delete file " + extractDir.toAbsolutePath().normalize(), e);
                    // swallow error as these old files don't matter really
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error listing contents of " + extractDir.toAbsolutePath().normalize());
        }
    }
}
