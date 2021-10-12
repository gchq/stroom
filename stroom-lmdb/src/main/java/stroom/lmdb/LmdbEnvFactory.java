package stroom.lmdb;

import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LmdbEnvFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbEnvFactory.class);

    private final PathCreator pathCreator;
    private final LmdbLibraryConfig lmdbLibraryConfig;
    private final TempDirProvider tempDirProvider;

    private final AtomicBoolean hasLibraryBeenConfigured = new AtomicBoolean(false);

    @Inject
    public LmdbEnvFactory(final PathCreator pathCreator,
                          final TempDirProvider tempDirProvider,
                          final LmdbLibraryConfig lmdbLibraryConfig) {
        this.pathCreator = pathCreator;
        this.lmdbLibraryConfig = lmdbLibraryConfig;
        this.tempDirProvider = tempDirProvider;
    }

    /**
     * @param dir The path where the environment will be located on the filesystem.
     *            Should be local disk and not shared storage.
     */
    public EnvironmentBuilder builder(final Path dir) {
        return new EnvironmentBuilder(
                pathCreator,
                tempDirProvider,
                lmdbLibraryConfig,
                dir,
                hasLibraryBeenConfigured);
    }

    /**
     * @param dir The path where the environment will be located on the filesystem,
     *            which can include the '~' character and system properties (e.g.
     *            '/${stroom.hom}/ref_data') which will be substituted.
     *            Should be local disk and not shared storage.
     */
    public EnvironmentBuilder builder(final String dir) {
        return new EnvironmentBuilder(
                pathCreator,
                tempDirProvider,
                lmdbLibraryConfig,
                Paths.get(pathCreator.makeAbsolute(pathCreator.replaceSystemProperties(dir))),
                hasLibraryBeenConfigured);
    }

    public static class EnvironmentBuilder {

        private final PathCreator pathCreator;
        private final TempDirProvider tempDirProvider;
        private final LmdbLibraryConfig lmdbLibraryConfig;
        private final Path dir;
        private final Env.Builder<ByteBuffer> builder;
        private final Set<EnvFlags> envFlags = new HashSet<>();

        private boolean isReaderBlockedByWriter = false;

        private EnvironmentBuilder(final PathCreator pathCreator,
                                   final TempDirProvider tempDirProvider,
                                   final LmdbLibraryConfig lmdbLibraryConfig,
                                   final Path dir,
                                   final AtomicBoolean hasLibraryBeenConfigured) {
            this.pathCreator = pathCreator;
            this.tempDirProvider = tempDirProvider;
            this.lmdbLibraryConfig = lmdbLibraryConfig;
            this.dir = dir;
            builder = Env.create();

            // Library config is done via java system props and is static code in LMDBJava so
            // only want to do it once
            if (!hasLibraryBeenConfigured.get()) {
                configureLibrary();
                hasLibraryBeenConfigured.set(true);
            }
        }

        public EnvironmentBuilder withMaxDbCount(final int maxDbCount) {
            builder.setMaxDbs(maxDbCount);
            return this;
        }

        public EnvironmentBuilder withMapSize(final ByteSize byteSize) {
            builder.setMapSize(byteSize.getBytes());
            return this;
        }

        public EnvironmentBuilder withMaxReaderCount(final int maxReaderCount) {
            builder.setMaxReaders(maxReaderCount);
            return this;
        }

        public EnvironmentBuilder addEnvFlag(final EnvFlags envFlag) {
            this.envFlags.add(envFlag);
            return this;
        }

        public EnvironmentBuilder withEnvFlags(final EnvFlags... envFlags) {
            this.envFlags.addAll(Arrays.asList(envFlags));
            return this;
        }

        public EnvironmentBuilder withEnvFlags(final Collection<EnvFlags> envFlags) {
            this.envFlags.addAll(envFlags);
            return this;
        }

        public EnvironmentBuilder makeWritersBlockReaders() {
            this.isReaderBlockedByWriter = true;
            return this;
        }

        public EnvironmentBuilder setIsReaderBlockedByWriter(final boolean isReaderBlockedByWriter) {
            this.isReaderBlockedByWriter = isReaderBlockedByWriter;
            return this;
        }

        private Optional<Path> getConfiguredLmdbLibraryPath() {
            return Optional.ofNullable(lmdbLibraryConfig.getProvidedSystemLibraryPath())
                    .map(pathStr ->
                            Paths.get(pathCreator.makeAbsolute(
                                    pathCreator.replaceSystemProperties(pathStr))));
        }

        private Path getLibraryExtractDir() {
            String extractDirStr = lmdbLibraryConfig.getSystemLibraryExtractDir();
            extractDirStr = pathCreator.replaceSystemProperties(extractDirStr);
            extractDirStr = pathCreator.makeAbsolute(extractDirStr);

            final String extractDirPropName = lmdbLibraryConfig.getFullPath(LmdbLibraryConfig.EXTRACT_DIR_PROP_NAME);

            Path extractDir;
            if (extractDirStr == null) {
                LOGGER.warn("LMDB system library extract dir is not set ({}), falling back to temporary directory {}.",
                        extractDirPropName,
                        tempDirProvider.get());

                extractDir = tempDirProvider.get();
                Objects.requireNonNull(extractDir, "Temp dir is not set");
                extractDir = extractDir.resolve(LmdbLibraryConfig.DEFAULT_LIBRARY_EXTRACT_SUB_DIR_NAME);
            } else {
                extractDirStr = pathCreator.replaceSystemProperties(extractDirStr);
                extractDirStr = pathCreator.makeAbsolute(extractDirStr);
                extractDir = Paths.get(extractDirStr);
            }

            try {
                LOGGER.info("Ensuring directory {} exists (from configuration property {})",
                        extractDir.toAbsolutePath(),
                        extractDirPropName);
                Files.createDirectories(extractDir);
            } catch (IOException e) {
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
                    } catch (IOException e) {
                        LOGGER.error("Unable to delete file " + extractDir.toAbsolutePath().normalize(), e);
                        // swallow error as these old files don't matter really
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error listing contents of " + extractDir.toAbsolutePath().normalize());
            }
        }

        public LmdbEnv build() {

            final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);

            final Env<ByteBuffer> env;
            try {
                env = builder.open(dir.toFile(), envFlagsArr);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error creating LMDB env at {}: {}", e.getMessage()), e);
            }

            return new LmdbEnv(dir, env, isReaderBlockedByWriter);
        }

        private void configureLibrary() {
            LOGGER.info("Configuring LMDB system library");

            final Path lmdbSystemLibraryPath = getConfiguredLmdbLibraryPath().orElse(null);

            if (lmdbSystemLibraryPath != null) {
                if (!Files.isReadable(lmdbSystemLibraryPath)) {
                    throw new RuntimeException("Unable to read LMDB system library at " +
                            lmdbSystemLibraryPath.toAbsolutePath().normalize());
                }
                // javax.validation should ensure the path is valid if set
                System.setProperty(
                        LmdbLibraryConfig.LMDB_NATIVE_LIB_PROP,
                        lmdbSystemLibraryPath.toAbsolutePath().normalize().toString());
                LOGGER.info("Using provided LMDB system library file " + lmdbSystemLibraryPath);
            } else {
                final Path systemLibraryExtractDir = getLibraryExtractDir();

                cleanUpExtractDir(systemLibraryExtractDir);

                // Set the location to extract the bundled LMDB binary to
                System.setProperty(
                        LmdbLibraryConfig.LMDB_EXTRACT_DIR_PROP,
                        systemLibraryExtractDir.toAbsolutePath().normalize().toString());
                LOGGER.info("Bundled LMDB system library binary will be extracted to " + systemLibraryExtractDir);
            }
        }
    }
}
