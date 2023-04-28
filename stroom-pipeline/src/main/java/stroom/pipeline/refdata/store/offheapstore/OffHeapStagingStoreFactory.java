package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.pipeline.refdata.ReferenceDataStagingLmdbConfig;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb.Factory;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.EnvFlags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton // So it can do a clean-up on boot
public class OffHeapStagingStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OffHeapStagingStoreFactory.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final LmdbEnvFactory lmdbEnvFactory;
    private final RefDataLmdbEnv refDataLmdbEnv;
    private final ByteBufferPool byteBufferPool;
    private final KeyValueStagingDb.Factory keyValueStagingDbFactory;
    private final RangeValueStagingDb.Factory rangeValueStagingDbFactory;
    private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final Provider<ReferenceDataStagingLmdbConfig> referenceDataStagingLmdbConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    public OffHeapStagingStoreFactory(
            final LmdbEnvFactory lmdbEnvFactory,
            final RefDataLmdbEnv refDataLmdbEnv,
            final ByteBufferPool byteBufferPool,
            final Factory keyValueStagingDbFactory,
            final RangeValueStagingDb.Factory rangeValueStagingDbFactory,
            final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory,
            final MapDefinitionUIDStore mapDefinitionUIDStore,
            final Provider<ReferenceDataStagingLmdbConfig> referenceDataStagingLmdbConfigProvider,
            final PathCreator pathCreator) {

        this.lmdbEnvFactory = lmdbEnvFactory;
        this.refDataLmdbEnv = refDataLmdbEnv;
        this.byteBufferPool = byteBufferPool;
        this.keyValueStagingDbFactory = keyValueStagingDbFactory;
        this.rangeValueStagingDbFactory = rangeValueStagingDbFactory;
        this.pooledByteBufferOutputStreamFactory = pooledByteBufferOutputStreamFactory;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
        this.referenceDataStagingLmdbConfigProvider = referenceDataStagingLmdbConfigProvider;
        this.pathCreator = pathCreator;

        final Path stagingEnvBaseDir = getStagingLmdbEnvBaseDir();
        if (Files.isDirectory(stagingEnvBaseDir)) {
            LOGGER.info("Deleting lingering reference data staging stores from {}", stagingEnvBaseDir);
            FileUtil.deleteContents(stagingEnvBaseDir);
        }
    }

    private Path getStagingLmdbEnvBaseDir() {
        return pathCreator.toAppPath(referenceDataStagingLmdbConfigProvider.get().getLocalDir());
    }

    /**
     * Create a {@link OffHeapStagingStore} for use by one thread to load a reference stream into.
     */
    public OffHeapStagingStore create(final RefStreamDefinition refStreamDefinition) {

        final LmdbEnv stagingLmdbEnv = buildStagingEnv(
                lmdbEnvFactory,
                refStreamDefinition);

        final KeyValueStagingDb keyValueStagingDb = keyValueStagingDbFactory.create(stagingLmdbEnv);
        final RangeValueStagingDb rangeValueStagingDb = rangeValueStagingDbFactory.create(stagingLmdbEnv);

        return new OffHeapStagingStore(
                stagingLmdbEnv,
                refDataLmdbEnv,
                keyValueStagingDb,
                rangeValueStagingDb,
                mapDefinitionUIDStore,
                pooledByteBufferOutputStreamFactory);
    }

    private LmdbEnv buildStagingEnv(final LmdbEnvFactory lmdbEnvFactory,
                                    final RefStreamDefinition refStreamDefinition) {
        final Path stagingEnvBaseDir = getStagingLmdbEnvBaseDir();

        // Dir needs to be unique to avoid any clashes. Add in the datetime, stream, part to help
        // with any debugging of lingering files.
        final String subDirName = DATE_FORMATTER.format(Instant.now())
                + "_" + refStreamDefinition.getStreamId()
                + "_" + refStreamDefinition.getPartNumber()
                + "_" + UUID.randomUUID();
        final Path subDirPath = stagingEnvBaseDir.resolve(subDirName);

        LOGGER.info("Creating temporary reference data staging LMDB environment in {}", subDirPath);
        try {
            // This will ensure the dir exists
            return lmdbEnvFactory.builder(referenceDataStagingLmdbConfigProvider.get())
                    .withMaxDbCount(2)
                    .withSubDirectory(subDirName)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error building staging LMDB in {}: {}",
                    subDirPath, e.getMessage()), e);
        }
    }
}
