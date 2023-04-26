package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.UnSortedDupKey;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.util.io.ByteSize;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * A transient store to load the ref entries from a single stream into. The purpose of this is to
 * isolate the loading of data into the main ref store from all the XML processing work in the pipeline,
 * with the aim of doing the least work possible inside the main ref store write txn. Also, as the entries
 * in this store are in the same key order as the main store we can transfer them into the main store
 * in sorted key order which can speed up the load significantly.
 */
public class OffHeapStagingStore implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OffHeapStagingStore.class);
    private static final int BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY = 2_000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final LmdbEnv stagingLmdbEnv;
    private final LmdbEnv refStoreLmdbEnv;
    private final KeyValueStagingDb keyValueStagingDb;
    private final RangeValueStagingDb rangeValueStagingDb;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final PooledByteBufferOutputStream pooledByteBufferOutputStream;

    private final Map<MapDefinition, UID> mapDefinitionToUIDMap = new HashMap<>();
    private final Map<UID, MapDefinition> uidToMapDefinitionMap = new HashMap<>();
    private final PooledByteBuffer keyPooledKeyBuffer;
    private final PooledByteBuffer rangePooledKeyBuffer;
    private final List<PooledByteBuffer> pooledByteBuffers = new ArrayList<>();
    private final BatchingWriteTxn batchingWriteTxn;
    private final UnsortedDupKeyFactory<KeyValueStoreKey> keyValueStoreKeyFactory;
    private final UnsortedDupKeyFactory<RangeStoreKey> rangeStoreKeyFactory;
    private boolean isComplete = false;

    public OffHeapStagingStore(final LmdbEnv stagingLmdbEnv,
                               final LmdbEnv refStoreLmdbEnv,
                               final KeyValueStagingDb keyValueStagingDb,
                               final RangeValueStagingDb rangeValueStagingDb,
                               final MapDefinitionUIDStore mapDefinitionUIDStore,
                               final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory) {
        this.stagingLmdbEnv = stagingLmdbEnv;
        this.refStoreLmdbEnv = refStoreLmdbEnv;
        this.keyValueStagingDb = keyValueStagingDb;
        this.rangeValueStagingDb = rangeValueStagingDb;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;


        // get buffers to (re)use for the life of this store
        this.keyPooledKeyBuffer = keyValueStagingDb.getPooledKeyBuffer();
        this.rangePooledKeyBuffer = rangeValueStagingDb.getPooledKeyBuffer();
        pooledByteBuffers.add(keyPooledKeyBuffer);
        pooledByteBuffers.add(rangePooledKeyBuffer);
        this.pooledByteBufferOutputStream = pooledByteBufferOutputStreamFactory
                .create(BUFFER_OUTPUT_STREAM_INITIAL_CAPACITY);

        batchingWriteTxn = stagingLmdbEnv.openBatchingWriteTxn(0);

        // Use default byte length of 4 which gives us 4billion ids.
        keyValueStoreKeyFactory = UnSortedDupKey.createFactory(KeyValueStoreKey.class);
        rangeStoreKeyFactory = UnSortedDupKey.createFactory(RangeStoreKey.class);
    }

    /**
     * Put an entry into the staging store. Entries with null values or duplicate keys will
     * all be stored.
     */
    void put(final MapDefinition mapDefinition,
             final String key,
             final RefDataValue refDataValue) {

        LOGGER.trace("put({}, {}, {}", mapDefinition, key, refDataValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(key);
        final StagingValue stagingValue = getStagingValue(refDataValue);
        keyPooledKeyBuffer.clear();
        checkComplete();

        // Get (or create) the UID from the main ref store to save us having to do this
        // later in the main ref store write txn
        final UID mapUid = getOrCreateUid(mapDefinition);

        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(mapUid, key);
        final UnSortedDupKey<KeyValueStoreKey> wrappedKey = keyValueStoreKeyFactory.createUnsortedKey(keyValueStoreKey);
        final ByteBuffer keyBuffer = keyPooledKeyBuffer.getByteBuffer();
        keyValueStagingDb.serializeKey(keyBuffer, wrappedKey);
        final ByteBuffer valueBuffer = stagingValue.getFullByteBuffer();

        batchingWriteTxn.processBatchItem(writeTxn -> {
            // Use the put method on the Dbi as we want to put directly without all the get/put stuff
            // to determine what was there before. Also, the db is set to MDB_DUPSORT so any dups in
            // the source data will be kept as dups in the db.
            try {
                keyValueStagingDb.getLmdbDbi().put(writeTxn, keyBuffer, valueBuffer);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message("""
                                Error putting key/value to staging store: {}
                                keyBuffer: {},
                                valueBuffer: {}""",
                        e.getMessage(),
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer)), e);
            }
        });
    }

    /**
     * Put an entry into the staging store. Entries with null values or duplicate keys will
     * all be stored.
     */
    void put(final MapDefinition mapDefinition,
             final Range<Long> keyRange,
             final RefDataValue refDataValue) {

        LOGGER.trace("put({}, {}, {}", mapDefinition, keyRange, refDataValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(keyRange);
        final StagingValue stagingValue = getStagingValue(refDataValue);
        rangePooledKeyBuffer.clear();
        checkComplete();

        // Get (or create) the UID from the main ref store to save us having to do this
        // later in the main ref store write txn
        final UID mapUid = getOrCreateUid(mapDefinition);

        final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, keyRange);
        final UnSortedDupKey<RangeStoreKey> wrappedKey = rangeStoreKeyFactory.createUnsortedKey(rangeStoreKey);
        final ByteBuffer keyBuffer = rangePooledKeyBuffer.getByteBuffer();
        rangeValueStagingDb.serializeKey(keyBuffer, wrappedKey);
        final ByteBuffer valueBuffer = stagingValue.getFullByteBuffer();

        batchingWriteTxn.processBatchItem(writeTxn -> {
            // Use the put method on the Dbi as we want to put directly without all the get/put stuff
            // to determine what was there before. Also, the db is set to MDB_DUPSORT so any dups in
            // the source data will be kept as dups in the db.
            try {
                rangeValueStagingDb.getLmdbDbi().put(writeTxn, keyBuffer, valueBuffer);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message("""
                                Error putting range/value to staging store: {}
                                keyBuffer: {},
                                valueBuffer: {}""",
                        e.getMessage(),
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer)), e);
            }
        });
    }

    private StagingValue getStagingValue(final RefDataValue refDataValue) {
        Objects.requireNonNull(refDataValue);
        // We could maybe convert from other RefDataValue types to StagingValue, but for now
        // this is fine.
        if (!(refDataValue instanceof StagingValue)) {
            throw new RuntimeException(LogUtil.message("Unexpected type {}, expecting {}",
                    refDataValue.getClass().getSimpleName(), StagingValue.class.getSimpleName()));
        }
        return (StagingValue) refDataValue;
    }

    /**
     * Call this when all puts have been carried out.
     */
    void completeLoad() {
        batchingWriteTxn.commit();
        batchingWriteTxn.close();
        isComplete = true;
    }

    /**
     * Loop over all entries in the key/value store. There may be multiple values for the
     * same key. Looping is done in key order, NOT in the oder they were originally put.
     */
    void forEachKeyValueEntry(final Consumer<Entry<KeyValueStoreKey, StagingValue>> entryStreamConsumer) {
        stagingLmdbEnv.doWithReadTxn(readTxn -> {
            keyValueStagingDb.forEachEntry(readTxn, KeyRange.all(), stagingEntry -> {
                final KeyValueStoreKey key = stagingEntry.getKey().getKey();
                final StagingValue stagingValue = stagingEntry.getValue();
                entryStreamConsumer.accept(Map.entry(key, stagingValue));
            });
        });
    }

    /**
     * Loop over all entries in the range/value store. There may be multiple values for the same range.
     * Looping is done in key order, NOT in the oder they were originally put.
     */
    void forEachRangeValueEntry(final Consumer<Entry<RangeStoreKey, StagingValue>> entryStreamConsumer) {
        stagingLmdbEnv.doWithReadTxn(readTxn -> {
            rangeValueStagingDb.forEachEntry(readTxn, KeyRange.all(), stagingEntry -> {
                final RangeStoreKey key = stagingEntry.getKey().getKey();
                final StagingValue stagingValue = stagingEntry.getValue();
                entryStreamConsumer.accept(Map.entry(key, stagingValue));
            });
        });
    }

    private void checkComplete() {
        if (isComplete) {
            throw new RuntimeException(LogUtil.message("OffHeapStagingStore is in a completed state"));
        }
    }

    private void clearKeyBuffers() {
        keyPooledKeyBuffer.clear();
        rangePooledKeyBuffer.clear();
    }

    private void clearBuffers() {
        pooledByteBufferOutputStream.clear();
        pooledByteBuffers.forEach(PooledByteBuffer::clear);
    }

    private UID getOrCreateUid(final MapDefinition mapDefinition) {
        // get the UID for this mapDefinition, and as we should only have a handful of mapDefinitions
        // per loader it makes sense to cache the MapDefinition=>UID mappings on heap for quicker access.
        final UID uid = mapDefinitionToUIDMap.computeIfAbsent(mapDefinition, mapDef -> {
            LOGGER.trace("MapDefinition not found in local cache so getting it from the store, {}", mapDefinition);
            // Here we use the main ref store env txn as we need a valid UID.
            final UID uidFromStore = refStoreLmdbEnv.getWithWriteTxn(writeTxn -> {
                // The temporaryUidPooledBuffer may not be used if we find the map def in the DB
                try (final PooledByteBuffer temporaryUidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer()) {

                    final PooledByteBuffer cachedUidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer();
                    // Add it to the list, so it will be released on close
                    pooledByteBuffers.add(cachedUidPooledBuffer);

                    // The returned UID wraps a direct buffer that is either owned by LMDB or came from
                    // temporaryUidPooledBuffer so as the txn is about to be closed we must copy it into
                    // another pooled buffer that we can cache for the life of this store.
                    final UID newUid = mapDefinitionUIDStore.getOrCreateUid(
                            writeTxn,
                            mapDef,
                            temporaryUidPooledBuffer);

                    // Now clone it into a different buffer and wrap in a new UID instance
                    final UID newUidClone = newUid.cloneToBuffer(cachedUidPooledBuffer.getByteBuffer());
                    return newUidClone;
                }
            });
            // Add the reverse mapping
            uidToMapDefinitionMap.put(uidFromStore, mapDef);
            return uidFromStore;
        });

        LOGGER.trace("Using mapUid {} for {}", uid, mapDefinition);
        return uid;
    }

    @Override
    public void close() throws Exception {
        closeAndSwallow(batchingWriteTxn, "batchingWriteTxn");
        closeAndSwallow(pooledByteBufferOutputStream, "pooledByteBufferOutputStream");

        pooledByteBuffers.forEach(pooledByteBuffer ->
                closeAndSwallow(pooledByteBuffer, "pooledByteBuffer"));

        stagingLmdbEnv.close();
        stagingLmdbEnv.delete();
    }

    /**
     * @return The map names loaded so far
     */
    List<String> getMapNames() {
        return mapDefinitionToUIDMap.keySet()
                .stream()
                .map(MapDefinition::getMapName)
                .collect(Collectors.toList());
    }

    private void closeAndSwallow(final AutoCloseable autoCloseable, final String name) {
        if (autoCloseable != null) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                LOGGER.error("Error closing {}: {}", name, e.getMessage(), e);
            }
        } else {
            LOGGER.debug("{} is null", name);
        }
    }

    MapDefinition getMapDefinition(final UID uid) {
        // If we know the uid then the mapping should already be in the map
        final MapDefinition mapDefinition = uidToMapDefinitionMap.get(uid);
        return Objects.requireNonNull(mapDefinition, () -> "We should have a mapDefinition for UID " + uid);
    }


    // --------------------------------------------------------------------------------


    public static class OffHeapStagingStoreFactory {

        private final TempDirProvider tempDirProvider;
        private final LmdbEnvFactory lmdbEnvFactory;
        private final ByteBufferPool byteBufferPool;
        private final KeyValueStagingDb.Factory keyValueStagingDbFactory;
        private final RangeValueStagingDb.Factory rangeValueStagingDbFactory;
        private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;

        @Inject
        public OffHeapStagingStoreFactory(
                final TempDirProvider tempDirProvider,
                final LmdbEnvFactory lmdbEnvFactory,
                final ByteBufferPool byteBufferPool,
                final KeyValueStagingDb.Factory keyValueStagingDbFactory,
                final RangeValueStagingDb.Factory rangeValueStagingDbFactory,
                final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory) {

            this.tempDirProvider = tempDirProvider;
            this.lmdbEnvFactory = lmdbEnvFactory;
            this.byteBufferPool = byteBufferPool;
            this.keyValueStagingDbFactory = keyValueStagingDbFactory;
            this.rangeValueStagingDbFactory = rangeValueStagingDbFactory;
            this.pooledByteBufferOutputStreamFactory = pooledByteBufferOutputStreamFactory;
        }

        public OffHeapStagingStore create(
                final LmdbEnv refStoreLmdbEnv,
                final MapDefinitionUIDStore mapDefinitionUIDStore,
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
                LOGGER.info("Creating reference data staging LMDB environment in {}/{}", stagingEnvDir, subDirName);
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
}
