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
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

            LOGGER.debug(() -> LogUtil.message("""
                            configureLibraryUnderLock() - lmdbLibraryConfig: {},
                              {},
                              {},
                              {},
                              {}""",
                    lmdbLibraryConfig,
                    dumpSystemProp(LmdbLibraryConfig.LMDB_EXTRACT_DIR_PROP),
                    dumpSystemProp(LmdbLibraryConfig.LMDB_NATIVE_LIB_PROP),
                    dumpSystemProp(LmdbLibraryConfig.JFFI_EXTRACT_DIR_PROP),
                    dumpSystemProp(LmdbLibraryConfig.JFFI_NATIVE_LIB_PROP)));

            final Path lmdbSystemLibraryPath = NullSafe.get(
                    lmdbLibraryConfig.getProvidedSystemLibraryPath(),
                    pathCreator::toAppPath);
            final Path jffiNativeLibraryPath = NullSafe.get(
                    lmdbLibraryConfig.getProvidedJffiLibraryPath(),
                    pathCreator::toAppPath);

            boolean extractLmdb = false;
            boolean extractJffi = false;

            if (lmdbSystemLibraryPath != null) {
                if (!Files.isReadable(lmdbSystemLibraryPath)) {
                    throw new RuntimeException("Unable to read LMDB system library at " + lmdbSystemLibraryPath);
                }
                // jakarta.validation should ensure the path is valid if set
                final String lmdbNativeLibProp = LmdbLibraryConfig.LMDB_NATIVE_LIB_PROP;
                System.setProperty(lmdbNativeLibProp, lmdbSystemLibraryPath.toString());
                LOGGER.info("Using provided LMDB native library file. Setting prop {} to '{}'",
                        lmdbNativeLibProp, lmdbSystemLibraryPath);
            } else {
                extractLmdb = true;
            }

            final String jffiNativeLibProp = LmdbLibraryConfig.JFFI_NATIVE_LIB_PROP;
            final String jffiPropVal = System.getenv(jffiNativeLibProp);
            if (NullSafe.isNonBlankString(jffiPropVal)) {
                LOGGER.info("Using provided JFFI native library file. Property {} already set to '{}'",
                        jffiNativeLibProp, jffiPropVal);
            } else if (jffiNativeLibraryPath != null) {
                if (!Files.isReadable(jffiNativeLibraryPath)) {
                    throw new RuntimeException("Unable to read JFFI native library at " + jffiNativeLibraryPath);
                }
                // jakarta.validation should ensure the path is valid if set
                System.setProperty(jffiNativeLibProp, jffiNativeLibraryPath.toString());
                LOGGER.info("Using provided JFFI native library file. Setting prop {} to '{}'",
                        jffiNativeLibProp, lmdbSystemLibraryPath);
            } else {
                extractJffi = true;
            }

            LOGGER.debug("configureLibraryUnderLock() - extractLmdb: {}, extractJffi: {}", extractLmdb, extractJffi);
            if (extractLmdb || extractJffi) {
                final Path systemLibraryExtractDir = getLibraryExtractDir(lmdbLibraryConfig);
                // LMDB/JFFI extract the lib on boot to a unique temp file and should delete on JVM exit,
                // but just in case clear out any old ones.
                cleanUpExtractDir(systemLibraryExtractDir);

                // Extract them both to the same place as both will need a non 'noexec' mount.

                // Set the location to extract the bundled LMDB binary to
                if (extractLmdb) {
                    final String lmdbExtractDirProp = LmdbLibraryConfig.LMDB_EXTRACT_DIR_PROP;
                    System.setProperty(lmdbExtractDirProp, systemLibraryExtractDir.toString());
                    LOGGER.info("Bundled LMDB native library binary will be extracted and used. " +
                                "Setting system prop '{}' to '{}'",
                            lmdbExtractDirProp, systemLibraryExtractDir);
                }

                // Set the location to extract the bundled Jffi binary to
                if (extractJffi) {
                    final String jffiExtractDirProp = LmdbLibraryConfig.JFFI_EXTRACT_DIR_PROP;
                    if (System.getProperty(jffiExtractDirProp) == null) {
                        // Allow a jvm arg to override our config
                        System.setProperty(jffiExtractDirProp, systemLibraryExtractDir.toString());
                        LOGGER.info("Bundled JFFI native library binary will be extracted and used. " +
                                    "Setting system prop '{}' to '{}'",
                                jffiExtractDirProp, systemLibraryExtractDir);
                    }
                }
            }
            HAS_LIBRARY_BEEN_CONFIGURED = true;
        } else {
            LOGGER.debug("Another thread beat us to it");
        }
    }

    private String dumpSystemProp(final String propName) {
        return "'" + propName + "': '" + System.getProperty(propName) + "'";
    }

    private Path getLibraryExtractDir(final LmdbLibraryConfig lmdbLibraryConfig) {
        final String extractDirStr = lmdbLibraryConfig.getSystemLibraryExtractDir();

        final String extractDirPropName = lmdbLibraryConfig.getFullPathStr(LmdbLibraryConfig.EXTRACT_DIR_PROP_NAME);

        Path extractDir;
        if (extractDirStr == null) {
            LOGGER.warn("LMDB system library extract dir is not set ({}), falling back to temporary directory {}. " +
                        "If the temporary directory is mounted with 'noexec' any use of LMDB will error.",
                    extractDirPropName,
                    tempDirProvider.get());

            extractDir = tempDirProvider.get();
            Objects.requireNonNull(extractDir, "Temp dir is not set");
            extractDir = extractDir.resolve(LmdbLibraryConfig.DEFAULT_LIBRARY_EXTRACT_SUB_DIR_NAME);
        } else {
            extractDir = pathCreator.toAppPath(extractDirStr);
        }
        extractDir = extractDir.toAbsolutePath().normalize();

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
        cleanUpExtractDir(extractDir, "lmdbjava-native-library-*.so", "LMDB native library");
        // The jffi file should not really ever be there because it is deleted once loaded, but just in case
        // See com.kenai.jffi.internal.StubLoader
        cleanUpExtractDir(extractDir, "jffi*.so", "JFFI native library");
    }

    private static void cleanUpExtractDir(final Path extractDir, final String glob, final String name) {
        try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(extractDir, glob)) {
            for (final Path file : directoryStream) {
                LOGGER.info("Deleting redundant {} file {}", name, file.toAbsolutePath().normalize());
                try {
                    Files.deleteIfExists(file);
                } catch (final IOException e) {
                    LOGGER.error(() -> LogUtil.message("Unable to delete {} file {}",
                            name, file.toAbsolutePath().normalize()), e);
                    // swallow error as these old files don't matter really
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error listing contents of {} with glob '{}'",
                    extractDir.toAbsolutePath().normalize(), glob));
        }
    }
}
