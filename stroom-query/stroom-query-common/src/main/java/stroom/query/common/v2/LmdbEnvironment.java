package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LmdbEnvironment {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvironment.class);

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";
    private static final String DEFAULT_STORE_SUB_DIR_NAME = "searchResults";

    private final Env<ByteBuffer> lmdbEnvironment;
    private final TempDirProvider tempDirProvider;
    private final LmdbConfig lmdbConfig;
    private final PathCreator pathCreator;
    final Path dbDir;
    private final ByteSize maxSize;
    private final int maxReaders;
    private final int maxPutsBeforeCommit;

    @Inject
    public LmdbEnvironment(final TempDirProvider tempDirProvider,
                           final LmdbConfig lmdbConfig,
                           final PathCreator pathCreator) {
        this.tempDirProvider = tempDirProvider;
        this.lmdbConfig = lmdbConfig;
        this.pathCreator = pathCreator;
        this.dbDir = getStoreDir();

        // Delete all DB files.
        FileUtil.deleteFile(dbDir.resolve("data.mdb"));
        FileUtil.deleteFile(dbDir.resolve("lock.mdb"));

        this.maxSize = lmdbConfig.getMaxStoreSize();
        this.maxReaders = lmdbConfig.getMaxReaders();
        this.maxPutsBeforeCommit = lmdbConfig.getMaxPutsBeforeCommit();
        this.lmdbEnvironment = createEnvironment(lmdbConfig);

        // Destroy all pre existing DBs for this environment.
        list();
        destroyAll();
        list();
    }

    private Env<ByteBuffer> createEnvironment(final LmdbConfig lmdbConfig) {
        LOGGER.info(
                "Creating RefDataOffHeapStore environment with [maxSize: {}, maxDbs: {}, dbDir {}, maxReaders {}, " +
                        "maxPutsBeforeCommit {}, isReadAheadEnabled {}]",
                maxSize,
                lmdbConfig.getMaxDbs(),
                FileUtil.getCanonicalPath(dbDir),
                maxReaders,
                maxPutsBeforeCommit,
                lmdbConfig.isReadAheadEnabled());

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
            envFlags = new EnvFlags[0];
        } else {
            envFlags = new EnvFlags[]{EnvFlags.MDB_NORDAHEAD};
        }

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

        return Env.create()
                .setMaxReaders(maxReaders)
                .setMapSize(maxSize.getBytes())
                .setMaxDbs(lmdbConfig.getMaxDbs())
                .open(dbDir.toFile(), envFlags);
    }

    void list() {
        LOGGER.info("Existing databases: \n\t{}",
                lmdbEnvironment.getDbiNames()
                        .stream()
                        .map(this::toString)
                        .collect(Collectors.joining("\n\t")));
    }

    private Path getStoreDir() {
        String storeDirStr = lmdbConfig.getLocalDir();
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

        return storeDir;
    }

    Dbi<ByteBuffer> openDbi(final String queryKey, final String instanceId) {
        final String name = queryKey + "_" + instanceId;
        LOGGER.debug("Opening LMDB database with name: {}", name);
        try {
            return lmdbEnvironment.openDbi(toBytes(name), DbiFlags.MDB_CREATE);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error opening LMDB database '{}' in '{}' ({})",
                    name,
                    FileUtil.getCanonicalPath(dbDir),
                    e.getMessage()),
                    e);
        }
    }

    int getMaxKeySize() {
        return lmdbEnvironment.getMaxKeySize();
    }

    Txn<ByteBuffer> txnWrite() {
        return lmdbEnvironment.txnWrite();
    }

    Txn<ByteBuffer> txnRead() {
        return lmdbEnvironment.txnRead();
    }

    private void destroyAll() {
        lmdbEnvironment
                .getDbiNames()
                .stream()
                .map(this::toString)
                .forEach(this::destroyNamed);
    }

    private void destroyNamed(final String name) {
        LOGGER.info("Destroying old search results for " + name);
        final Dbi<ByteBuffer> dbi = lmdbEnvironment.openDbi(name);
        dbi.drop(txnWrite(), true);
        dbi.close();
    }

    private String toString(final byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] toBytes(final String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
