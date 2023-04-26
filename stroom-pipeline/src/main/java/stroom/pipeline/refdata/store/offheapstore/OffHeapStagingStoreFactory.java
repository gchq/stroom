package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb.Factory;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.util.io.ByteSize;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.EnvFlags;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.inject.Inject;

public class OffHeapStagingStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OffHeapStagingStoreFactory.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final TempDirProvider tempDirProvider;
    private final LmdbEnvFactory lmdbEnvFactory;
    private final ByteBufferPool byteBufferPool;
    private final KeyValueStagingDb.Factory keyValueStagingDbFactory;
    private final RangeValueStagingDb.Factory rangeValueStagingDbFactory;
    private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;

    @Inject
    public OffHeapStagingStoreFactory(
            final TempDirProvider tempDirProvider,
            final LmdbEnvFactory lmdbEnvFactory,
            final ByteBufferPool byteBufferPool,
            final Factory keyValueStagingDbFactory,
            final RangeValueStagingDb.Factory rangeValueStagingDbFactory,
            final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory,
            final MapDefinitionUIDStore mapDefinitionUIDStore) {

        this.tempDirProvider = tempDirProvider;
        this.lmdbEnvFactory = lmdbEnvFactory;
        this.byteBufferPool = byteBufferPool;
        this.keyValueStagingDbFactory = keyValueStagingDbFactory;
        this.rangeValueStagingDbFactory = rangeValueStagingDbFactory;
        this.pooledByteBufferOutputStreamFactory = pooledByteBufferOutputStreamFactory;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
    }

    /**
     * Create a {@link OffHeapStagingStore} for use by one thread to load a reference stream into.
     */
    public OffHeapStagingStore create(
            final RefDataLmdbEnv refStoreLmdbEnv,
            final RefStreamDefinition refStreamDefinition) {

        final LmdbEnv stagingLmdbEnv = buildStagingEnv(
                tempDirProvider,
                lmdbEnvFactory,
                refStreamDefinition);

        final KeyValueStagingDb keyValueStagingDb = keyValueStagingDbFactory.create(stagingLmdbEnv);
        final RangeValueStagingDb rangeValueStagingDb = rangeValueStagingDbFactory.create(stagingLmdbEnv);

        return new OffHeapStagingStore(
                stagingLmdbEnv,
                refStoreLmdbEnv,
                keyValueStagingDb,
                rangeValueStagingDb,
                mapDefinitionUIDStore,
                pooledByteBufferOutputStreamFactory);
    }

    private LmdbEnv buildStagingEnv(final TempDirProvider tempDirProvider,
                                    final LmdbEnvFactory lmdbEnvFactory,
                                    final RefStreamDefinition refStreamDefinition) {
        // TODO: 19/04/2023 Maybe get a dir from config
        // TODO: 19/04/2023 Create an LmdbConfig impl for ref staging
        final Path stagingEnvDir = tempDirProvider.get().resolve("ref-data-staging");

        // Dir needs to be quite unique to avoid any clashes
        final String subDirName = DATE_FORMATTER.format(Instant.now())
                + "-" + refStreamDefinition.getStreamId()
                + "-" + refStreamDefinition.getPartNumber()
                + "-" + UUID.randomUUID();

        try {
            LOGGER.info("Creating reference data staging LMDB environment in {}/{}",
                    stagingEnvDir,
                    subDirName);

            return lmdbEnvFactory.builder(stagingEnvDir)
                    .withMapSize(ByteSize.ofGibibytes(50))
                    .withMaxDbCount(128)
                    .setIsReaderBlockedByWriter(false)
                    .withSubDirectory(subDirName)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error building staging LMDB in {}/{}: {}",
                    stagingEnvDir, subDirName, e.getMessage()), e);
        }
    }
}
