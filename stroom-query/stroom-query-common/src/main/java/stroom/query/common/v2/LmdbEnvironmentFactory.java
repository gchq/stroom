package stroom.query.common.v2;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LmdbEnvironmentFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvironmentFactory.class);

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";

    private final LmdbConfig lmdbConfig;
    private final PathCreator pathCreator;
    private final Path dbDir;

    @Inject
    public LmdbEnvironmentFactory(final LmdbConfig lmdbConfig,
                                  final PathCreator pathCreator) {
        this.lmdbConfig = lmdbConfig;
        this.pathCreator = pathCreator;
        this.dbDir = getStoreDir();

        final String lmdbSystemLibraryPath = lmdbConfig.getLmdbSystemLibraryPath();
        if (lmdbSystemLibraryPath != null) {
            // javax.validation should ensure the path is valid if set
            System.setProperty(LMDB_NATIVE_LIB_PROP, lmdbSystemLibraryPath);
            LOGGER.info("Using provided LMDB system library file " + lmdbSystemLibraryPath);
        } else {
            // Set the location to extract the bundled LMDB binary to
            System.setProperty(LMDB_EXTRACT_DIR_PROP, dbDir.toAbsolutePath().toString());
            LOGGER.info("Extracting bundled LMDB binary to " + dbDir);
        }
    }

    private Path getStoreDir() {
        final Path storeDir = pathCreator.toAppPath(lmdbConfig.getLocalDir());
        try {
            LOGGER.debug("Ensuring directory {}", storeDir);
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException("Error ensuring store directory " +
                    FileUtil.getCanonicalPath(storeDir) +
                    " exists", e);
        }

        LOGGER.debug("Deleting contents {}", storeDir);
        // Delete contents.
        if (!FileUtil.deleteContents(storeDir)) {
            throw new RuntimeException("Error deleting contents of " +
                    FileUtil.getCanonicalPath(storeDir));
        }

        return storeDir;
    }

    public LmdbEnvironment createEnvironment(final String dirName) {
        final Path dir = dbDir.resolve(dirName);
        FileUtil.mkdirs(dir);

        LOGGER.debug(() ->
                "Creating LMDB environment for search results with [maxSize: " +
                        lmdbConfig.getMaxStoreSize() +
                        ", maxDbs: " +
                        lmdbConfig.getMaxDbs() +
                        ", dbDir " +
                        FileUtil.getCanonicalPath(dir) +
                        ", maxReaders " +
                        lmdbConfig.getMaxReaders() +
                        ", " +
                        "maxPutsBeforeCommit " +
                        lmdbConfig.getMaxPutsBeforeCommit() +
                        ", isReadAheadEnabled " +
                        lmdbConfig.isReadAheadEnabled() +
                        "]");

        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.

        // NOTE on setMapSize() from LMDB author found on https://groups.google.com/forum/#!topic/caffe-users/0RKsTTYRGpQ
        // On Windows the OS sets the filesize equal to the mapsize. (MacOS requires that too, and allocates
        // all of the physical space up front, it doesn't support sparse files.) The mapsize should not be
        // hardcoded into software, it needs to be reconfigurable. On Windows and MacOS you really shouldn't
        // set it larger than the amount of free space on the filesystem.

        final EnvFlags[] envFlags;
        if (lmdbConfig.isReadAheadEnabled()) {
            //EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_MAPASYNC};
            envFlags = new EnvFlags[]{};
        } else {
            //, EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_MAPASYNC};
            envFlags = new EnvFlags[]{EnvFlags.MDB_NORDAHEAD};
        }

        final Env<ByteBuffer> env = Env.create()
                .setMaxReaders(lmdbConfig.getMaxReaders())
                .setMapSize(lmdbConfig.getMaxStoreSize().getBytes())
                .setMaxDbs(1)
                .open(dir.toFile(), envFlags);
        return new LmdbEnvironment(dir, env);
    }
}
