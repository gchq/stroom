package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.PutOutcome;
import stroom.lmdb.UnSortedDupKey;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.StagingDb;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private final LmdbEnv stagingLmdbEnv;
    private final RefDataLmdbEnv refStoreLmdbEnv;
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

    public OffHeapStagingStore(@Assisted final LmdbEnv stagingLmdbEnv,
                               final RefDataLmdbEnv refStoreLmdbEnv,
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
             final StagingValue stagingValue) {

        LOGGER.trace("put({}, {}, {}", mapDefinition, key, stagingValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(key);
        keyPooledKeyBuffer.clear();
        checkComplete();

        // Get (or create) the UID from the main ref store to save us having to do this
        // later in the main ref store write txn
        final UID mapUid = getOrCreateUid(mapDefinition);

        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(mapUid, key);
        final UnSortedDupKey<KeyValueStoreKey> wrappedKey = keyValueStoreKeyFactory.createUnsortedKey(keyValueStoreKey);
        final ByteBuffer keyBuffer = keyPooledKeyBuffer.getByteBuffer();

        doPut(wrappedKey, keyBuffer, stagingValue, keyValueStagingDb);
    }

    /**
     * Put an entry into the staging store. Entries with null values or duplicate keys will
     * all be stored.
     */
    void put(final MapDefinition mapDefinition,
             final Range<Long> keyRange,
             final StagingValue stagingValue) {

        LOGGER.trace("put({}, {}, {}", mapDefinition, keyRange, stagingValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(keyRange);
        rangePooledKeyBuffer.clear();
        checkComplete();

        // Get (or create) the UID from the main ref store to save us having to do this
        // later in the main ref store write txn
        final UID mapUid = getOrCreateUid(mapDefinition);

        final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, keyRange);
        final UnSortedDupKey<RangeStoreKey> wrappedKey = rangeStoreKeyFactory.createUnsortedKey(rangeStoreKey);
        final ByteBuffer keyBuffer = rangePooledKeyBuffer.getByteBuffer();

        doPut(wrappedKey, keyBuffer, stagingValue, rangeValueStagingDb);
    }

    private <K> void doPut(final K key,
                           final ByteBuffer keyBuffer,
                           final StagingValue stagingValue,
                           final StagingDb<K> stagingDb) {

        stagingDb.serializeKey(keyBuffer, key);
        final ByteBuffer valueBuffer = stagingValue.getFullByteBuffer();

        batchingWriteTxn.processBatchItem(writeTxn -> {
            // Use the put method on the Dbi as we want to put directly without all the get/put stuff
            // to determine what was there before. Also, the db is set to MDB_DUPSORT so any dups in
            // the source data will be kept as dups in the db.
            try {
                final PutOutcome putOutcome = stagingDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        false,
                        false);
                if (!putOutcome.isSuccess()) {
                    throw new RuntimeException(LogUtil.message("Unsuccessful putOutcome {} putting entry to {}",
                            putOutcome, stagingDb.getDbName()));
                }
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message("""
                                Error putting entry to staging store (db: {}): {}
                                keyBuffer: {},
                                valueBuffer: {}""",
                        stagingDb.getDbName(),
                        e.getMessage(),
                        ByteBufferUtils.byteBufferInfo(keyBuffer),
                        ByteBufferUtils.byteBufferInfo(valueBuffer)), e);
            }
        });
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
        if (!batchingWriteTxn.isClosed()) {
            closeAndSwallow(batchingWriteTxn, "batchingWriteTxn");
        }
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
}
