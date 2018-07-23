/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import com.google.inject.assistedinject.Assisted;
import org.apache.commons.io.FileUtils;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.properties.StroomPropertyService;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.offheapstore.databases.RangeStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RefDataOffHeapStore implements RefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    public static final long PROCESSING_INFO_UPDATE_DELAY_MS = Duration.of(1, ChronoUnit.HOURS).toMillis();

    private static final String DATA_RETENTION_AGE_PROP_KEY = "stroom.refloader.offheapstore.deleteAge";
    private static final String DATA_RETENTION_AGE_DEFAULT_VALUE = "30d";

    private final Path dbDir;
    private final long maxSize;
    private final int maxReaders;
    private final int maxPutsBeforeCommit;
    private final int valueBufferCapacity;


    private final Env<ByteBuffer> lmdbEnvironment;

    // the DBs that make up the store
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStoreDb valueStoreDb;
    private final MapUidForwardDb mapUidForwardDb;
    private final MapUidReverseDb mapUidReverseDb;
    private final ProcessingInfoDb processingInfoDb;
//    private final ValueReferenceCountDb valueReferenceCountDb;

    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final StroomPropertyService stroomPropertyService;

    // For synchronising access to the data belonging to a MapDefinition
    private final Striped<Lock> refStreamDefStripedReentrantLock;

    private final ByteBufferPool byteBufferPool;

    /**
     * @param dbDir   The directory the LMDB environment will be created in, it must already exist
     * @param maxSize The max size in bytes of the environment. This should be less than the available
     */
    @Inject
    RefDataOffHeapStore(
            @Assisted final Path dbDir,
            @Assisted final long maxSize,
            @Assisted("maxReaders") final int maxReaders,
            @Assisted("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
            @Assisted("valueBufferCapacity") final int valueBufferCapacity,
            final ByteBufferPool byteBufferPool,
            final KeyValueStoreDb.Factory keyValueStoreDbFactory,
            final ValueStoreDb.Factory valueStoreDbFactory,
            final RangeStoreDb.Factory rangeStoreDbFactory,
            final MapUidForwardDb.Factory mapUidForwardDbFactory,
            final MapUidReverseDb.Factory mapUidReverseDbFactory,
            final ProcessingInfoDb.Factory processingInfoDbFactory,
//            final ValueReferenceCountDb.Factory valueReferenceCountDbFactory,
            final StroomPropertyService stroomPropertyService) {

        this.dbDir = dbDir;
        this.maxSize = maxSize;
        this.maxReaders = maxReaders;
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.valueBufferCapacity = valueBufferCapacity;

        LOGGER.info(
                "Creating RefDataOffHeapStore with maxSize: {}, dbDir {}, maxReaders {}, maxPutsBeforeCommit {}, valueBufferCapacity {}",
                FileUtils.byteCountToDisplaySize(maxSize),
                dbDir.toAbsolutePath().toString(),
                maxReaders,
                maxPutsBeforeCommit,
                FileUtils.byteCountToDisplaySize(valueBufferCapacity));

        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.

        // NOTE on setMapSize() from LMDB author found on https://groups.google.com/forum/#!topic/caffe-users/0RKsTTYRGpQ
        // On Windows the OS sets the filesize equal to the mapsize. (MacOS requires that too, and allocates
        // all of the physical space up front, it doesn't support sparse files.) The mapsize should not be
        // hardcoded into software, it needs to be reconfigurable. On Windows and MacOS you really shouldn't
        // set it larger than the amount of free space on the filesystem.
        lmdbEnvironment = Env.<ByteBuffer>create()
                .setMaxReaders(maxReaders)
                .setMapSize(maxSize)
                .setMaxDbs(7) //should equal the number of DBs we create which is fixed at compile time
                .open(dbDir.toFile());

        // create all the databases
        this.keyValueStoreDb = keyValueStoreDbFactory.create(lmdbEnvironment);
        this.rangeStoreDb = rangeStoreDbFactory.create(lmdbEnvironment);
        this.valueStoreDb = valueStoreDbFactory.create(lmdbEnvironment);
        this.mapUidForwardDb = mapUidForwardDbFactory.create(lmdbEnvironment);
        this.mapUidReverseDb = mapUidReverseDbFactory.create(lmdbEnvironment);
        this.processingInfoDb = processingInfoDbFactory.create(lmdbEnvironment);
//        this.valueReferenceCountDb = valueReferenceCountDbFactory.create(lmdbEnvironment);

        this.mapDefinitionUIDStore = new MapDefinitionUIDStore(lmdbEnvironment, mapUidForwardDb, mapUidReverseDb);
        this.stroomPropertyService = stroomPropertyService;
        this.byteBufferPool = byteBufferPool;

        this.refStreamDefStripedReentrantLock = Striped.lazyWeakLock(100);
    }

    /**
     * Returns the {@link RefDataProcessingInfo} for the passed {@link MapDefinition}, or an empty
     * {@link Optional} if there isn't one.
     */
    @Override
    public Optional<RefDataProcessingInfo> getAndMutateProcessingInfo(final RefStreamDefinition refStreamDefinition) {
        // get the current processing info
        final Optional<RefDataProcessingInfo> optProcessingInfo = processingInfoDb.get(refStreamDefinition);

        // update the last access time, but only do it if it has been a while since we last did it to avoid
        // opening writeTxn all the time. The last accessed time is not critical as far as accuracy goes. As long
        // as it is reasonably accurate we can use it for purging old data.
        optProcessingInfo.ifPresent(processingInfo -> {
            long timeSinceLastAccessedTimeMs = System.currentTimeMillis() - processingInfo.getLastAccessedTimeEpochMs();
            if (timeSinceLastAccessedTimeMs > PROCESSING_INFO_UPDATE_DELAY_MS) {
                processingInfoDb.updateLastAccessedTime(refStreamDefinition);
            }
        });
        LOGGER.trace("getProcessingInfo({}) - {}", refStreamDefinition, optProcessingInfo);
        return optProcessingInfo;
    }

    @Override
    public boolean isDataLoaded(final RefStreamDefinition refStreamDefinition) {

        boolean result = getAndMutateProcessingInfo(refStreamDefinition)
                .map(RefDataProcessingInfo::getProcessingState)
                .filter(Predicate.isEqual(ProcessingState.COMPLETE))
                .isPresent();

        LOGGER.trace("isDataLoaded({}) - {}", refStreamDefinition, result);
        return result;
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                           final String key) {

        //TODO refactor to share the pooled key buffer
        Optional<RefDataValue> optionalRefDataValue = byteBufferPool.getWithBuffer(
                lmdbEnvironment.getMaxKeySize(), keyBuffer ->
                        LmdbUtils.getWithReadTxn(lmdbEnvironment, readTxn ->
                                getValueStoreKey(readTxn, mapDefinition, key)
                                        .flatMap(valueStoreKey ->
                                                valueStoreDb.get(readTxn, valueStoreKey))));

        LOGGER.trace("getValue({}, {}) - {}", mapDefinition, key, optionalRefDataValue);
        return optionalRefDataValue;
    }

//    public Optional<RefDataValue> getValue(final ValueStoreKey valueStoreKey) {
//        return valueStoreDb.get(valueStoreKey);
//    }

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {

//        return LmdbUtils.getWithReadTxn(lmdbEnvironment, readTxn -> {
//            Optional<ValueStoreKey> optValueStoreKey = getValueStoreKey(readTxn, mapDefinition, key);
//
//            // return a RefDataValueProxy if we found a value.
//            return optValueStoreKey.map(valueStoreKey ->
//                    new RefDataValueProxy(this, valueStoreKey, mapDefinition));
//        });
        return new SingleRefDataValueProxy(this, mapDefinition, key);
    }

    private Optional<ByteBuffer> getValueStoreKey(final Txn<ByteBuffer> readTxn,
                                                  final MapDefinition mapDefinition,
                                                  final String key) {
        LOGGER.trace("getValueStoreKey({}, {})", mapDefinition, key);

        // TODO we could could consider a short lived on-heap cache for this as it
        // will be hit MANY times for the same entry
        final Optional<UID> optMapUid = mapUidForwardDb.get(readTxn, mapDefinition);

        Optional<ByteBuffer> optValueStoreKey;
        if (optMapUid.isPresent()) {
            LOGGER.trace("Found map UID {}", optMapUid);
            //do the lookup in the kv store first
            final UID mapUid = optMapUid.get();
            final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(optMapUid.get(), key);
            final ByteBuffer keyValueStoreKeyBuf = keyValueStoreDb.getKeySerde().serialize(keyValueStoreKey);
            optValueStoreKey = keyValueStoreDb.getAsBytes(readTxn, keyValueStoreKeyBuf);

            if (!optValueStoreKey.isPresent()) {
                //not found in the kv store so look in the keyrange store instead

                try {
                    // speculative lookup in the range store. At this point we don't know if we have
                    // any ranges for this mapdef or not, but either way we need a call to LMDB so
                    // just do the range lookup
                    final long keyLong = Long.parseLong(key);

                    // look up our long key in the range store to see if it is part of a range
                    optValueStoreKey = rangeStoreDb.getAsBytes(readTxn, mapUid, keyLong);

                } catch (NumberFormatException e) {
                    // key could not be converted to a long, either this mapdef has no ranges or
                    // an invalid key was used. See if we have any ranges at all for this mapdef
                    // to determine whether to error or not.
                    boolean doesStoreContainRanges = rangeStoreDb.containsMapDefinition(readTxn, mapUid);
                    if (doesStoreContainRanges) {
                        // we have ranges for this map def so we would expect to be able to convert the key
                        throw new RuntimeException(LambdaLogger.buildMessage(
                                "Key {} cannot be used with the range store as it cannot be converted to a long", key), e);
                    }
                    // no ranges for this map def so the fact that we could not convert the key to a long
                    // is not a problem. Do nothing.
                }
            }
        } else {
            LOGGER.debug("Couldn't find map UID which means the data for this map has not been loaded or the map name is wrong {}",
                    mapDefinition);
            // no map UID so can't look in key/range stores without one
            optValueStoreKey = Optional.empty();
        }
        return optValueStoreKey;
    }

//    @Override
//    public void consumeValue(final MapDefinition mapDefinition,
//                             final String key,
//                             final Consumer<RefDataValue> valueConsumer) {
//
//
//    }

    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {


        // lookup the passed mapDefinition and key and if a valueStoreKey is found use that to
        // lookup the value in the value store, passing the actual value part to the consumer.
        // The consumer gets only the value, not the type or ref count and has to understand how
        // to interpret the bytes in the buffer
        boolean wasValueFound = LmdbUtils.getWithReadTxn(lmdbEnvironment, txn ->
                getValueStoreKey(txn, mapDefinition, key)
                        .flatMap(valueStoreKeyBuf ->
                                valueStoreDb.getValueBytes(txn, valueStoreKeyBuf))
                        .map(valueBuf -> {
                            valueBytesConsumer.accept(valueBuf);
                            return true;
                        })
                        .orElse(false));

        LOGGER.trace("consumeValueBytes({}, {}) - {}", mapDefinition, key, wasValueFound);
        return wasValueFound;
    }

//    @Override
//    public void consumeValue(final ValueStoreKey valueStoreKey, final Consumer<RefDataValue> valueConsumer) {
//
//    }
//
//    @Override
//    public void consumeBytes(final ValueStoreKey valueStoreKey, final Consumer<ByteBuffer> valueConsumer) {
//
//    }
//
//    @Override
//    public <T> Optional<T> map(final MapDefinition mapDefinition,
//                               final String key,
//                               final Function<RefDataValue, T> valueMapper) {
//
//        return Optional.empty();
//    }
//
//    @Override
//    public <T> Optional<T> map(final ValueStoreKey valueStoreKey, final Function<RefDataValue, T> valueMapper) {
//        return Optional.empty();
//    }
//
//    @Override
//    public <T> Optional<T> mapBytes(final ValueStoreKey valueStoreKey, final Function<ByteBuffer, T> valueMapper) {
//        return Optional.empty();
//    }


    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     */
    RefDataLoader loader(final RefStreamDefinition refStreamDefinition,
                         final long effectiveTimeMs) {
        //TODO should we pass in an ErrorReceivingProxy so we can log errors with it?
        RefDataLoader refDataLoader = new RefDataLoaderImpl(
                this,
                byteBufferPool,
                refStreamDefStripedReentrantLock,
                keyValueStoreDb,
                rangeStoreDb,
                valueStoreDb,
                mapDefinitionUIDStore,
                processingInfoDb,
//                valueReferenceCountDb,
                lmdbEnvironment,
                refStreamDefinition,
                effectiveTimeMs);

        refDataLoader.setCommitInterval(maxPutsBeforeCommit);
        return refDataLoader;
    }

    @Override
    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                              final long effectiveTimeMs,
                                              final Consumer<RefDataLoader> work) {

        boolean result = false;
        try (RefDataLoader refDataLoader = loader(refStreamDefinition, effectiveTimeMs)) {
            // we now hold the lock for this RefStreamDefinition so test the completion state

            if (isDataLoaded(refStreamDefinition)) {
                LOGGER.debug("Data is already loaded for {}, so doing nothing", refStreamDefinition);
            } else {
                work.accept(refDataLoader);
                result = true;
            }
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Error closing refDataLoader for {}", refStreamDefinition), e);
        }
        return result;
    }

    @Override
    public long getKeyValueEntryCount() {
        return keyValueStoreDb.getEntryCount();
    }

    @Override
    public long getKeyRangeValueEntryCount() {
        return rangeStoreDb.getEntryCount();
    }

    @Override
    @StroomSimpleCronSchedule(cron = "2 * *") // 02:00 every day
    @JobTrackedSchedule(
            jobName = "Ref Data Store Purge",
            description = "Purge old reference data form the off heap store, as defined by " + DATA_RETENTION_AGE_PROP_KEY)
    public void purgeOldData() {


        try (final PooledByteBuffer currRefStreamDefPooledBuf = getKeyBufferFromPool();
             final PooledByteBuffer accessTimeThresholdPooledBuf = getAccessTimeCutOffBuffer()) {

            final ByteBuffer currRefStreamDefBuf = currRefStreamDefPooledBuf.getByteBuffer();
            final ByteBuffer accessTimeThresholdBuf = accessTimeThresholdPooledBuf.getByteBuffer();

//            currentRefStreamDefBuf = getKeyBufferFromPool();
//            accessTimeThresholdBuf = getAccessTimeCutOffBuffer();

            // hack to make it final for the lambda
//            final ByteBuffer accessTimeThresholdBufFinal = accessTimeThresholdBuf;

            boolean wasMatchFound = false;
            do {
                wasMatchFound = LmdbUtils.getWithWriteTxn(lmdbEnvironment, writeTxn -> {
                    final Optional<ByteBufferPair> byteBufferPair = processingInfoDb.getNextEntry(
                            writeTxn,
                            currRefStreamDefBuf,
                            processingInfoBuffer ->
                                    RefDataProcessingInfoSerde.wasAccessedAfter(
                                            processingInfoBuffer,
                                            accessTimeThresholdBuf));

                    if (!byteBufferPair.isPresent()) {
                        // no matching ref streams found so break out
                        return false;
                    } else {
                        // found a ref stream def that is ready for purge

                        // mark it is purge in progress
                        processingInfoDb.updateProcessingState(writeTxn,
                                currRefStreamDefPooledBuf.getByteBuffer(),
                                ProcessingState.PURGE_IN_PROGRESS,
                                false);

                        // purge the data associated with this ref stream def
                        purgeRefStreamData(writeTxn, currRefStreamDefBuf);

                        //now delete the proc info entry
                        processingInfoDb.delete(currRefStreamDefBuf);
                        return true;
                    }
                });
            } while (wasMatchFound);
        }


        //TODO

        //open a write txn
        //open a cursor on the process info table to scan all records
        //subtract purge age prop val from current time to give purge cut off ms
        //for each proc info record one test the last access time against the cut off time (without de-serialising to long)
        //if it is older than cut off date then change its state to PURGE_IN_PROGRESS


        //process needs to be idempotent so we can continue a part finished purge. A new txn MUST always check the
        //processing info state to ensure it is still PURGE_IN_PROGRESS in case another txn has started a load, in which
        //case we won't purge. A purge txn must wrap at least the deletion of the key(range)/entry, the value (if no other
        //refs). The deletion of the mapdef<=>uid paiur must be done in a txn to ensure consistency.
        //Each processing info entry should be be fetched with a read txn, then get a StripedSemaphore for the streamdef
        //then open the write txn. This should stop any conflict with load jobs for that stream.

        //when overwrites happen we may have two values that had an association with same mapDef + key.  The above process
        //will only remove the currently associated value.  We would have to scan the whole value table to look for


        // streamDef => mapDefs
        // mapDef => mapUID
        // mapUID => ValueKeys
        // ValueKey => value

        // <pipe uuid 2-18><pipe ver 2-18><stream id 8> => <create time 8><last access time 8><effective time 8><state 1>
        // <pipe uuid 12-18><pipe ver 2-18><stream id 8><map name ?> => <mapUID 4>
        // <mapUID 4> => <pipe uuid 2-18><pipe ver 2-18><stream id 8><map name ?>
        // <mapUID 4><string Key ?> => <valueHash 4><id 2>
        // <mapUID 4><range start 8><range end 8> => <valueHash 4><id 2>
        // <valueHash 4><id 2> => <value type 1><reference count 4><value bytes ?>

        // increment ref count when
        // - putting new key(Range)/Value entry + new value entry (set initial ref count at 1)
        // - putting new key(Range)/Value entry with existing value entry
        // - overwrite key(Range)/Value entry (+1 on new value key)

        // decrement ref count when
        // - overwrite key(Range)/Value entry (-1 on old value key)
        // - delete key(Range)/Value entry

        // change to ref counter MUST be done in same txn as the thing that is making it change, e.g the KV entry removal
    }

    private void purgeRefStreamData(final Txn<ByteBuffer> writeTxn,
                                    final ByteBuffer refStreamDefBuffer) {

        Optional<UID> optMapUid = Optional.empty();
        do {
            //open a ranged cursor on the map forward table to scan all map defs for that stream def
            //for each map def get the map uid
            optMapUid = mapDefinitionUIDStore.getNextMapDefinition(writeTxn, refStreamDefBuffer);
            optMapUid.ifPresent(byteBuffer ->
                    purgeMapData(writeTxn, byteBuffer));
        } while (optMapUid.isPresent());
    }

    private void purgeMapData(final Txn<ByteBuffer> writeTxn,
                              final UID mapUid) {
        //open a cursor on key/value store scanning over all record for this map uid
        //for each one get the valueStoreKey
        //look up the valueStoreKey in the references DB and establish if this is the last ref for this value
        //if it is, delete the value entry
        //now delete the key/value entry
        //do the same for the keyrange/value DB
        //commit every N key/value or keyrange/value entries
        //now delete the mapdef<=>uid pair

    }

    public void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition, final Runnable work) {
        final Lock lock = refStreamDefStripedReentrantLock.get(refStreamDefinition);
        try {
            lock.lockInterruptibly();
            try {
                // now we have sole access to this RefStreamDefinition so perform the work on it
                work.run();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.warn("Thread interrupted waiting to acquire lock for {}", refStreamDefinition);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    void logAllContents() {
        processingInfoDb.logDatabaseContents();
        mapUidForwardDb.logDatabaseContents();
        mapUidReverseDb.logDatabaseContents();
        keyValueStoreDb.logDatabaseContents();
        rangeStoreDb.logDatabaseContents();
        valueStoreDb.logDatabaseContents();
//        valueReferenceCountDb.logDatabaseContents();
    }

    /**
     * Must be returned to the pool in a finally block
     */
    private PooledByteBuffer getKeyBufferFromPool() {
        return byteBufferPool.getBufferAsResource(lmdbEnvironment.getMaxKeySize());
    }

    private PooledByteBuffer getAccessTimeCutOffBuffer() {
        long purgeAge = ModelStringUtil.parseDurationString(
                stroomPropertyService.getProperty(DATA_RETENTION_AGE_PROP_KEY, DATA_RETENTION_AGE_DEFAULT_VALUE));
        long purgeCutOff = System.currentTimeMillis() - purgeAge;

        LOGGER.info("Using purge cut off {}", Instant.ofEpochMilli(purgeCutOff));

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getBufferAsResource(Long.BYTES);
        pooledByteBuffer.getByteBuffer().putLong(purgeCutOff);
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    /**
     * Must be returned to the pool in a finally block
     */
    private ByteBufferPair getBufferPairFromPool() {
        return byteBufferPool.getBufferPair(lmdbEnvironment.getMaxKeySize(), valueBufferCapacity);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * Class for adding multiple items to the {@link RefDataOffHeapStore} within a single
     * write transaction.  Must be used inside a try-with-resources block to ensure the transaction
     * is closed, e.g.
     * try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }
     * The transaction will be committed when the loader is closed
     */
    public static class RefDataLoaderImpl implements RefDataLoader {


        private Txn<ByteBuffer> writeTxn = null;
        private final RefDataOffHeapStore refDataOffHeapStore;
        private final ByteBufferPool byteBufferPool;
        private final Lock refStreamDefReentrantLock;

        private final KeyValueStoreDb keyValueStoreDb;
        private final RangeStoreDb rangeStoreDb;
        private final ValueStoreDb valueStoreDb;
        private final MapDefinitionUIDStore mapDefinitionUIDStore;
        private final ProcessingInfoDb processingInfoDb;
//        private final ValueReferenceCountDb valueReferenceCountDb;

        private final Env<ByteBuffer> lmdbEnvironment;
        private final RefStreamDefinition refStreamDefinition;
        private final long effectiveTimeMs;
        private int maxPutsBeforeCommit = Integer.MAX_VALUE;
        private int putsCounter = 0;
        private int successfulPutsCounter = 0;
        private boolean overwriteExisting = false;
        private Instant startTime = Instant.EPOCH;
        private LoaderState currentLoaderState = LoaderState.NEW;

        // TODO we could just hit lmdb each time, but there may be serde costs
        private final Map<MapDefinition, UID> mapDefinitionToUIDMap = new HashMap<>();

        private enum LoaderState {
            NEW,
            INITIALISED,
            COMPLETED,
            CLOSED
        }

        private RefDataLoaderImpl(final RefDataOffHeapStore refDataOffHeapStore,
                                  final ByteBufferPool byteBufferPool,
                                  final Striped<Lock> refStreamDefStripedReentrantLock,
                                  final KeyValueStoreDb keyValueStoreDb,
                                  final RangeStoreDb rangeStoreDb,
                                  final ValueStoreDb valueStoreDb,
                                  final MapDefinitionUIDStore mapDefinitionUIDStore,
                                  final ProcessingInfoDb processingInfoDb,
//                                  final ValueReferenceCountDb valueReferenceCountDb,
                                  final Env<ByteBuffer> lmdbEnvironment,
                                  final RefStreamDefinition refStreamDefinition,
                                  final long effectiveTimeMs) {

            this.refDataOffHeapStore = refDataOffHeapStore;
            this.byteBufferPool = byteBufferPool;
            this.keyValueStoreDb = keyValueStoreDb;
            this.rangeStoreDb = rangeStoreDb;
            this.valueStoreDb = valueStoreDb;
            this.processingInfoDb = processingInfoDb;
//            this.valueReferenceCountDb = valueReferenceCountDb;

            this.mapDefinitionUIDStore = mapDefinitionUIDStore;
            this.lmdbEnvironment = lmdbEnvironment;
            this.refStreamDefinition = refStreamDefinition;
            this.effectiveTimeMs = effectiveTimeMs;

            // Get the lock for this refStreamDefinition
            // This will make any other threads trying to load the same refStreamDefinition block and wait for us

            this.refStreamDefReentrantLock = refStreamDefStripedReentrantLock.get(refStreamDefinition);
            LAMBDA_LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        try {
                            LOGGER.debug("Acquiring lock");
                            refStreamDefReentrantLock.lockInterruptibly();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Load aborted due to thread interruption");
                        }
                    },
                    () -> LambdaLogger.buildMessage("Acquiring lock for {}", refStreamDefinition));
        }

        @Override
        public RefStreamDefinition getRefStreamDefinition() {
            return refStreamDefinition;
        }

        @Override
        public boolean initialise(final boolean overwriteExisting) {
            //TODO we could do all this in the ctor?

            LOGGER.debug("initialise called, overwriteExisting: {}", overwriteExisting);
            checkCurrentState(LoaderState.NEW);

            startTime = Instant.now();

            // TODO create processed streams entry if it doesn't exist with a state of IN_PROGRESS
            // TODO if it does exist update the update time

//            beginTxn();
            this.overwriteExisting = overwriteExisting;


            // TODO once inside the protection of the semaphore we need to (somewhere) recheck the
            //processing info state to see if we need to continue.  Prob outside here.


//            if (!overwriteExisting) {
//                final RefDataProcessingInfo currentRefDataProcessingInfo = processingInfoDb.get(refStreamDefinition);
//            }

            final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    effectiveTimeMs,
                    ProcessingState.LOAD_IN_PROGRESS);

            // TODO need to consider how to prevent multiple threads trying to load the same
            // ref data set at once

            // TODO do we want to overwrite the existing value or just update its state/lastAccessedTime?
            // use its own short live txn just to set the value so other readers can see it.
            boolean didPutSucceed = processingInfoDb.put(
                    refStreamDefinition, refDataProcessingInfo, overwriteExisting);

            currentLoaderState = LoaderState.INITIALISED;
            return didPutSucceed;
        }

        @Override
        public void completeProcessing() {
            LOGGER.trace("Completing processing (put count {})", putsCounter);
            checkCurrentState(LoaderState.INITIALISED);
            beginTxnIfRequired();

            // Set the processing info record to COMPLETE and update the last update time
            processingInfoDb.updateProcessingState(
                    writeTxn, refStreamDefinition, ProcessingState.COMPLETE, true);

            final Duration loadDuration = Duration.between(startTime, Instant.now());
            final String mapNames = mapDefinitionToUIDMap.keySet()
                    .stream()
                    .map(MapDefinition::getMapName)
                    .collect(Collectors.joining(","));
            LOGGER.info("Successfully Loaded {} entries out of {} attempts with map names [{}] in {} for {}",
                    successfulPutsCounter, putsCounter, mapNames, loadDuration, refStreamDefinition);
            currentLoaderState = LoaderState.COMPLETED;
        }

        @Override
        public void setCommitInterval(final int maxPutsBeforeCommit) {
            Preconditions.checkArgument(maxPutsBeforeCommit >= 1);
            this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        }

        @Override
        public boolean put(final MapDefinition mapDefinition,
                           final String key,
                           final RefDataValue refDataValue) {
            LOGGER.trace("put({}, {}, {}", mapDefinition, key, refDataValue);

            Objects.requireNonNull(mapDefinition);
            Objects.requireNonNull(key);
            Objects.requireNonNull(refDataValue);

            checkCurrentState(LoaderState.INITIALISED);
            beginTxnIfRequired();


            final UID mapUid = getOrCreateUid(mapDefinition);
            final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(mapUid, key);

            // see if we have a value already for this key
            // if overwrite == false, we can just drop out here
            // if overwrite == true we need to de-reference the value (and maybe delete)
            // then create a new value, assuming they are different
            Optional<ValueStoreKey> optCurrentValueStoreKey = keyValueStoreDb.get(keyValueStoreKey);

            // TODO May be able to create direct buffer for reuse over all write ops to save the cost
            // of buffer allocation, see info here https://github.com/lmdbjava/lmdbjava/issues/81
            // If the loader holds a key and value ByteBuffer then they can be used for all write ops
            // See TestByteBufferReusePerformance


            boolean didPutSucceed;
            if (optCurrentValueStoreKey.isPresent()) {
                if (!overwriteExisting) {
                    // already have an entry for this key so drop out here
                    // with nothing to do as we can't overwrite anything
                    didPutSucceed = false;
                } else {
                    // overwriting and we already have a value so see if the old and
                    // new values are the same.
                    ValueStoreKey currentValueStoreKey = optCurrentValueStoreKey.get();

//                    RefDataValue currentValueStoreValue = valueStoreDb.get(writeTxn, currentValueStoreKey)
//                            .orElseThrow(() -> new RuntimeException("Should have a value at this point"));

                    boolean areValuesEqual = valueStoreDb.areValuesEqual(
                            writeTxn, currentValueStoreKey, refDataValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        didPutSucceed = true;
                    } else {
                        // value is different so we need to de-reference the old one
                        // and getOrCreate the new one
                        valueStoreDb.deReferenceValue(writeTxn, currentValueStoreKey);

                        //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                        final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(
                                writeTxn, refDataValue, overwriteExisting);

                        // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
                        // do a get then optional put.
                        didPutSucceed = keyValueStoreDb.put(
                                writeTxn, keyValueStoreKey, valueStoreKey, overwriteExisting);
                    }
                }
            } else {
                // no existing valueStoreKey so create the entries
                //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(
                        writeTxn, refDataValue, overwriteExisting);

                // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
                // do a get then optional put.
                didPutSucceed = keyValueStoreDb.put(
                        writeTxn, keyValueStoreKey, valueStoreKey, overwriteExisting);
            }

            if (didPutSucceed) {
                successfulPutsCounter++;
            }

            commitIfRequired();

            return didPutSucceed;
        }

        @Override
        public boolean put(final MapDefinition mapDefinition,
                           final Range<Long> keyRange,
                           final RefDataValue refDataValue) {
            LOGGER.trace("put({}, {}, {}", mapDefinition, keyRange, refDataValue);
            Objects.requireNonNull(mapDefinition);
            Objects.requireNonNull(keyRange);
            Objects.requireNonNull(refDataValue);

            checkCurrentState(LoaderState.INITIALISED);
            beginTxnIfRequired();

            final UID mapUid = getOrCreateUid(mapDefinition);
            final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, keyRange);


            // see if we have a value already for this key
            // if overwrite == false, we can just drop out here
            // if overwrite == true we need to de-reference the value (and maybe delete)
            // then create a new value, assuming they are different
            Optional<ValueStoreKey> optCurrentValueStoreKey = rangeStoreDb.get(rangeStoreKey);

            boolean didPutSucceed;
            if (optCurrentValueStoreKey.isPresent()) {
                if (!overwriteExisting) {
                    // already have an entry for this key so drop out here
                    // with nothing to do as we can't overwrite anything
                    didPutSucceed = false;
                } else {
                    // overwriting and we already have a value so see if the old and
                    // new values are the same.
                    ValueStoreKey currentValueStoreKey = optCurrentValueStoreKey.get();

                    boolean areValuesEqual = valueStoreDb.areValuesEqual(
                            writeTxn, currentValueStoreKey, refDataValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        didPutSucceed = true;
                    } else {
                        // value is different so we need to de-reference the old one
                        // and getOrCreate the new one
                        valueStoreDb.deReferenceValue(writeTxn, currentValueStoreKey);

                        //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                        final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(
                                writeTxn, refDataValue, overwriteExisting);

                        // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
                        // do a get then optional put.
                        didPutSucceed = rangeStoreDb.put(
                                writeTxn, rangeStoreKey, valueStoreKey, overwriteExisting);
                    }
                }
            } else {
                // no existing valueStoreKey so create the entries
                //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(
                        writeTxn, refDataValue, overwriteExisting);

                // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
                // do a get then optional put.
                didPutSucceed = rangeStoreDb.put(
                        writeTxn, rangeStoreKey, valueStoreKey, overwriteExisting);
            }

            if (didPutSucceed) {
                successfulPutsCounter++;
            }
            commitIfRequired();
            return didPutSucceed;
        }

        @Override
        public void close() {
            LOGGER.trace("Close called for {}", refStreamDefinition);

            if (currentLoaderState.equals(LoaderState.INITIALISED)) {
                LOGGER.warn(LambdaLogger.buildMessage("Reference data loader for {} was initialised but then closed before being completed",
                        refStreamDefinition));
            }
            if (writeTxn != null) {
                LOGGER.trace("Committing transaction (put count {})", putsCounter);
                writeTxn.commit();
                writeTxn.close();
            }
            currentLoaderState = LoaderState.CLOSED;

            LOGGER.debug("Releasing semaphore permit for {}", refStreamDefinition);
            refStreamDefReentrantLock.unlock();

            // uncomment this for development testing, handy for seeing what is in the stores post load
//            refDataOffHeapStore.logAllContents();
        }

        private void beginTxn() {
            if (writeTxn != null) {
                throw new RuntimeException("Transaction is already open");
            }
            LOGGER.trace("Beginning write transaction");
            this.writeTxn = lmdbEnvironment.txnWrite();
        }

        private void commit() {
            if (writeTxn != null) {
                try {
                    LOGGER.trace("Committing (put count {})", putsCounter);
                    writeTxn.commit();
                    writeTxn = null;
                } catch (Exception e) {
                    throw new RuntimeException("Error committing write transaction", e);
                }
            }
        }

        private UID getOrCreateUid(final MapDefinition mapDefinition) {
            // TODO we may have only just started a txn so if the UIDs in the cache wrap LMDB owned
            // buffers bad things could happen.  We could clear the cache on commit, or put clones
            // into the cache. Have added clone() call below, but needs testing.

            // get the UID for this mapDefinition, and as we should only have a handful of mapDefinitions
            // per loader it makes sense to cache the MapDefinition=>UID mappings on heap for quicker access.
            final UID uid = mapDefinitionToUIDMap.computeIfAbsent(mapDefinition, mapDef -> {
                LOGGER.trace("MapDefinition not found in local cache so getting it from the store, {}", mapDefinition);
                // cloning the UID in case we leave the txn
                beginTxnIfRequired();
                return mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDef).clone();
            });
            return uid;
        }

        /**
         * To be called after each put
         */
        private void commitIfRequired() {
            putsCounter++;
            if (putsCounter % maxPutsBeforeCommit == 0) {
                LOGGER.trace("Committing with putsCounter {}, maxPutsBeforeCommit {}", putsCounter, maxPutsBeforeCommit);
                commit();
            }
        }

        private void beginTxnIfRequired() {
            if (writeTxn == null) {
                beginTxn();
            }
        }

        private void checkCurrentState(final LoaderState... validStates) {
            boolean isCurrentStateValid = false;
            for (LoaderState loaderState : validStates) {
                if (currentLoaderState.equals(loaderState)) {
                    isCurrentStateValid = true;
                    break;
                }
            }
            if (!isCurrentStateValid) {
                throw new IllegalStateException(LambdaLogger.buildMessage("Current loader state: {}, valid states: {}",
                        currentLoaderState, Arrays.toString(validStates)));
            }
        }
    }

    public interface Factory {
        RefDataStore create(
                final Path dbDir,
                final long maxSize,
                @Assisted("maxReaders") final int maxReaders,
                @Assisted("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                @Assisted("valueBufferCapacity") final int valueBufferCapacity);
    }
}
