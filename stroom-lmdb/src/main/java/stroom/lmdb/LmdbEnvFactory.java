package stroom.lmdb;

import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

public class LmdbEnvFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbEnvFactory.class);

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";

    private final PathCreator pathCreator;

    @Inject
    public LmdbEnvFactory(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    public EnvironmentBuilder builder(final Path dir) {
        return new EnvironmentBuilder(pathCreator, dir);
    }

    public EnvironmentBuilder builder(final String dir) {
        return new EnvironmentBuilder(
                pathCreator,
                Paths.get(pathCreator.makeAbsolute(pathCreator.replaceSystemProperties(dir))));
    }

    public static class EnvironmentBuilder {

        private final PathCreator pathCreator;
        private final Path dir;
        private final Env.Builder<ByteBuffer> builder;
        private final Set<EnvFlags> envFlags = new HashSet<>();
        private boolean doWritersBlockReaders = false;

        private Path lmdbSystemLibraryPath = null;

        private EnvironmentBuilder(final PathCreator pathCreator, final Path dir) {
            this.pathCreator = pathCreator;
            this.dir = dir;
            builder = Env.create();
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

        public EnvironmentBuilder withLmdbSystemLibraryPath(final Path lmdbSystemLibraryPath) {
            this.lmdbSystemLibraryPath = lmdbSystemLibraryPath;
            return this;
        }

        public EnvironmentBuilder withLmdbSystemLibraryPath(final String lmdbSystemLibraryPath) {
            this.lmdbSystemLibraryPath = lmdbSystemLibraryPath == null
                    ? null
                    : Paths.get(pathCreator.makeAbsolute(
                            pathCreator.replaceSystemProperties(lmdbSystemLibraryPath)));
            return this;
        }

        public EnvironmentBuilder makeWritersBlockReaders() {
            this.doWritersBlockReaders = true;
            return this;
        }

        public LmdbEnv build() {

            if (lmdbSystemLibraryPath != null) {
                if (!Files.isReadable(lmdbSystemLibraryPath)) {
                    throw new RuntimeException("Unable to read LMDB system library at " +
                            lmdbSystemLibraryPath.toAbsolutePath().normalize());
                }
                // javax.validation should ensure the path is valid if set
                System.setProperty(
                        LMDB_NATIVE_LIB_PROP,
                        lmdbSystemLibraryPath.toAbsolutePath().normalize().toString());
                LOGGER.info("Using provided LMDB system library file " + lmdbSystemLibraryPath);
            } else {
                // Set the location to extract the bundled LMDB binary to
                System.setProperty(
                        LMDB_EXTRACT_DIR_PROP,
                        dir.toAbsolutePath().normalize().toString());
                LOGGER.info("Extracting bundled LMDB binary to " + dir);
            }

            final EnvFlags[] envFlagsArr = envFlags.toArray(new EnvFlags[0]);

            final Env<ByteBuffer> env;
            try {
                env = builder.open(dir.toFile(), envFlagsArr);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error creating LMDB env at {}: {}", e.getMessage()), e);
            }

            return new LmdbEnv(dir, env, doWritersBlockReaders);
        }
    }
}
