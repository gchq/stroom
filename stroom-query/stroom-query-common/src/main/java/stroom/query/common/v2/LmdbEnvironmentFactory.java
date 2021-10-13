package stroom.query.common.v2;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LmdbEnvironmentFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvironmentFactory.class);

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";
    private static final String DEFAULT_STORE_SUB_DIR_NAME = "searchResults";

    private final TempDirProvider tempDirProvider;
    private final ResultStoreConfig resultStoreConfig;
    private final PathCreator pathCreator;
    private final Path dbDir;

    @Inject
    public LmdbEnvironmentFactory(final TempDirProvider tempDirProvider,
                                  final ResultStoreConfig resultStoreConfig,
                                  final PathCreator pathCreator) {
        this.tempDirProvider = tempDirProvider;
        this.resultStoreConfig = resultStoreConfig;
        this.pathCreator = pathCreator;
        this.dbDir = getStoreDir();

        final String lmdbSystemLibraryPath = resultStoreConfig.getLmdbSystemLibraryPath();
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
        String storeDirStr = resultStoreConfig.getLocalDir();
        storeDirStr = pathCreator.replaceSystemProperties(storeDirStr);
        storeDirStr = pathCreator.makeAbsolute(storeDirStr);
        Path storeDir;
        if (storeDirStr == null) {
            LOGGER.info("Off heap store dir is not set, falling back to {}", tempDirProvider.get());
            storeDir = tempDirProvider.get();
            Objects.requireNonNull(storeDir, "Temp dir is not set");
            storeDir = storeDir.resolve(DEFAULT_STORE_SUB_DIR_NAME);
        } else {
            storeDirStr = pathCreator.replaceSystemProperties(storeDirStr);
            storeDirStr = pathCreator.makeAbsolute(storeDirStr);
            storeDir = Paths.get(storeDirStr);
        }

        try {
            LOGGER.debug("Ensuring directory {}", storeDir);
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring store directory {} exists", storeDirStr), e);
        }

        LOGGER.debug("Deleting contents {}", storeDir);
        // Delete contents.
        if (!FileUtil.deleteContents(storeDir)) {
            throw new RuntimeException(LogUtil.message("Error deleting contents of {}", storeDirStr));
        }

        return storeDir;
    }

    public LmdbEnvironment createEnvironment(final String dirName) {
        final Path dir = dbDir.resolve(dirName);
        FileUtil.mkdirs(dir);

        LOGGER.debug(() ->
                "Creating LMDB environment for search results with [maxSize: " +
                        resultStoreConfig.getMaxStoreSize() +
                        ", maxDbs: " +
                        resultStoreConfig.getMaxDbs() +
                        ", dbDir " +
                        FileUtil.getCanonicalPath(dir) +
                        ", maxReaders " +
                        resultStoreConfig.getMaxReaders() +
                        ", " +
                        "maxPutsBeforeCommit " +
                        resultStoreConfig.getMaxPutsBeforeCommit() +
                        ", isReadAheadEnabled " +
                        resultStoreConfig.isReadAheadEnabled() +
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

        // MDB_NOTLS means the reader slots created in LMDB are tied to the tx and not the thread.
        // As we are often using thread pools, threads no longer doing LMDB work may be holding on
        // to reader slots.  NOTLS seems to be the default in the python and go LMDB libs.
        if (resultStoreConfig.isReadAheadEnabled()) {
            envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS};
        } else {
            envFlags = new EnvFlags[]{EnvFlags.MDB_NOTLS, EnvFlags.MDB_NORDAHEAD};
        }

        final Env<ByteBuffer> env = Env.create()
                .setMaxReaders(resultStoreConfig.getMaxReaders())
                .setMapSize(resultStoreConfig.getMaxStoreSize().getBytes())
                .setMaxDbs(1)
                .open(dir.toFile(), envFlags);
        return new LmdbEnvironment(dir, env);
    }
}
