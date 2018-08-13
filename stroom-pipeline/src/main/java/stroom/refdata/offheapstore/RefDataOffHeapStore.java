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

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import com.google.inject.assistedinject.Assisted;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import org.apache.commons.io.FileUtils;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.properties.StroomPropertyService;
import stroom.refdata.lmdb.LmdbDb;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.offheapstore.databases.RangeStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreMetaDb;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefDataOffHeapStore implements RefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    static final String DATA_RETENTION_AGE_PROP_KEY = "stroom.refloader.offheapstore.purgeAge";
    private static final String DATA_RETENTION_AGE_DEFAULT_VALUE = "30d";

    public static final long PROCESSING_INFO_UPDATE_DELAY_MS = Duration.of(1, ChronoUnit.HOURS).toMillis();

    private final Path dbDir;
    private final long maxSize;
    private final int maxReaders;
    private final int maxPutsBeforeCommit;
    private final int valueBufferCapacity;

    private final Env<ByteBuffer> lmdbEnvironment;

    // the DBs that make up the store
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ProcessingInfoDb processingInfoDb;

    // classes that front multiple DBs
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;

    private final StroomPropertyService stroomPropertyService;
    private final Map<String, LmdbDb> databaseMap = new HashMap<>();

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
            final ValueStoreMetaDb.Factory valueStoreMetaDbFactory,
            final RangeStoreDb.Factory rangeStoreDbFactory,
            final MapUidForwardDb.Factory mapUidForwardDbFactory,
            final MapUidReverseDb.Factory mapUidReverseDbFactory,
            final ProcessingInfoDb.Factory processingInfoDbFactory,
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
        ValueStoreDb valueStoreDb = valueStoreDbFactory.create(lmdbEnvironment);
        MapUidForwardDb mapUidForwardDb = mapUidForwardDbFactory.create(lmdbEnvironment);
        MapUidReverseDb mapUidReverseDb = mapUidReverseDbFactory.create(lmdbEnvironment);
        this.processingInfoDb = processingInfoDbFactory.create(lmdbEnvironment);
        ValueStoreMetaDb valueStoreMetaDb = valueStoreMetaDbFactory.create(lmdbEnvironment);

        // hold all the DBs in a map so we can get at them by name
        addDbsToMap(
                keyValueStoreDb,
                rangeStoreDb,
                valueStoreDb,
                mapUidForwardDb,
                mapUidReverseDb,
                processingInfoDb,
                valueStoreMetaDb);

        this.valueStore = new ValueStore(lmdbEnvironment, valueStoreDb, valueStoreMetaDb);
        this.mapDefinitionUIDStore = new MapDefinitionUIDStore(lmdbEnvironment, mapUidForwardDb, mapUidReverseDb);

        this.stroomPropertyService = stroomPropertyService;
        this.byteBufferPool = byteBufferPool;

        this.refStreamDefStripedReentrantLock = Striped.lazyWeakLock(100);
    }

    private void addDbsToMap(final LmdbDb... lmdbDbs) {
        for (LmdbDb lmdbDb : lmdbDbs) {
            this.databaseMap.put(lmdbDb.getDbName(), lmdbDb);
        }
    }

    @Override
    public Optional<RefDataProcessingInfo> getAndTouchProcessingInfo(final RefStreamDefinition refStreamDefinition) {
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

        boolean result = getAndTouchProcessingInfo(refStreamDefinition)
                .map(RefDataProcessingInfo::getProcessingState)
                .filter(Predicate.isEqual(ProcessingState.COMPLETE))
                .isPresent();

        LOGGER.trace("isDataLoaded({}) - {}", refStreamDefinition, result);
        return result;
    }

    /**
     * Returns true if this {@link MapDefinition} exists in the store. It makes no guarantees about the state
     * of the data.
     *
     * @param mapDefinition
     */
    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        return mapDefinitionUIDStore.exists(mapDefinition);
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                           final String key) {


        // Use the mapDef to get a mapUid, then use the mapUid and key
        // to do a lookup in the keyValue or rangeValue stores. The resulting
        // value store key buffer can then be used to get the actual value.
        // The value is then deserialised while still inside the txn.
        try (PooledByteBuffer valueStoreKeyPooledBufferClone = valueStore.getPooledKeyBuffer()) {
            Optional<RefDataValue> optionalRefDataValue =
                    LmdbUtils.getWithReadTxn(lmdbEnvironment, readTxn ->
                            getValueStoreKey(readTxn, mapDefinition, key)
                                    .flatMap(valueStoreKeyBuffer -> {
                                        // we are going to use the valueStoreKeyBuffer as a key in multiple
                                        // get() calls so need to clone it first.
                                        ByteBuffer valueStoreKeyBufferClone = valueStoreKeyPooledBufferClone.getByteBuffer();
                                        ByteBufferUtils.copy(valueStoreKeyBuffer, valueStoreKeyBufferClone);
                                        return Optional.of(valueStoreKeyBufferClone);
                                    })
                                    .flatMap(valueStoreKeyBuffer ->
                                            valueStore.get(readTxn, valueStoreKeyBuffer)));

            LOGGER.trace("getValue({}, {}) - {}", mapDefinition, key, optionalRefDataValue);
            return optionalRefDataValue;
        }
    }

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {

        return new SingleRefDataValueProxy(this, mapDefinition, key);
    }

    /**
     * Intended only for testing use.
     */
    void setLastAccessedTime(final RefStreamDefinition refStreamDefinition, long timeMs) {
        processingInfoDb.updateLastAccessedTime(refStreamDefinition, timeMs);
    }

    private Optional<ByteBuffer> getValueStoreKey(final Txn<ByteBuffer> readTxn,
                                                  final MapDefinition mapDefinition,
                                                  final String key) {
        LOGGER.trace("getValueStoreKey({}, {})", mapDefinition, key);

        // TODO we could could consider a short lived on-heap cache for this as it
        // will be hit MANY times for the same entry
        final Optional<UID> optMapUid = mapDefinitionUIDStore.get(readTxn, mapDefinition);

        Optional<ByteBuffer> optValueStoreKeyBuffer;
        if (optMapUid.isPresent()) {
            LOGGER.trace("Found map UID {}", optMapUid);
            //do the lookup in the kv store first
            final UID mapUid = optMapUid.get();
            final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(optMapUid.get(), key);

            optValueStoreKeyBuffer = keyValueStoreDb.getAsBytes(readTxn, keyValueStoreKey);

            if (!optValueStoreKeyBuffer.isPresent()) {
                //not found in the kv store so look in the keyrange store instead

                try {
                    // speculative lookup in the range store. At this point we don't know if we have
                    // any ranges for this mapdef or not, but either way we need a call to LMDB so
                    // just do the range lookup
                    final long keyLong = Long.parseLong(key);

                    // look up our long key in the range store to see if it is part of a range
                    optValueStoreKeyBuffer = rangeStoreDb.getAsBytes(readTxn, mapUid, keyLong);

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
            optValueStoreKeyBuffer = Optional.empty();
        }
        return optValueStoreKeyBuffer;
    }


    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {

        // lookup the passed mapDefinition and key and if a valueStoreKey is found use that to
        // lookup the value in the value store, passing the actual value part to the consumer.
        // The consumer gets only the value, not the type or ref count and has to understand how
        // to interpret the bytes in the buffer

        try (PooledByteBuffer valueStoreKeyPooledBufferClone = valueStore.getPooledKeyBuffer()) {
            boolean wasValueFound = LmdbUtils.getWithReadTxn(lmdbEnvironment, txn ->
                    getValueStoreKey(txn, mapDefinition, key)
                            .flatMap(valueStoreKeyBuffer -> {
                                // we are going to use the valueStoreKeyBuffer as a key in multiple
                                // get() calls so need to clone it first.
                                ByteBuffer valueStoreKeyBufferClone = valueStoreKeyPooledBufferClone.getByteBuffer();
                                ByteBufferUtils.copy(valueStoreKeyBuffer, valueStoreKeyBufferClone);
                                return Optional.of(valueStoreKeyBufferClone);
                            })
                            .flatMap(valueStoreKeyBuf ->
                                    valueStore.getTypedValueBuffer(txn, valueStoreKeyBuf))
                            .map(valueBuf -> {
                                valueBytesConsumer.accept(valueBuf);
                                return true;
                            })
                            .orElse(false));

            LOGGER.trace("consumeValueBytes({}, {}) - {}", mapDefinition, key, wasValueFound);
            return wasValueFound;
        }
    }

//    private Optional<TypedByteBuffer> getTypedValueBuffer(final Txn<ByteBuffer> txn, final ByteBuffer valueStoreKeyBuffer) {
//        OptionalInt optTypeId = valueStoreMetaDb.getTypeId(txn, valueStoreKeyBuffer);
//        if (optTypeId.isPresent()) {
//            ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, valueStoreKeyBuffer)
//                    .orElseThrow(() -> new RuntimeException(
//                            "If we have a meta entry we should also have a value entry, data may be corrupted"));
//
//            return Optional.of(new TypedByteBuffer(optTypeId.getAsInt(), valueBuffer));
//        } else {
//            return Optional.empty();
//        }
//    }

    @Override
    public void purgeOldData() {
        purgeOldData(System.currentTimeMillis());
    }

    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     */
    private RefDataLoader loader(final RefStreamDefinition refStreamDefinition,
                                 final long effectiveTimeMs) {
        //TODO should we pass in an ErrorReceivingProxy so we can log errors with it?
        RefDataLoader refDataLoader = new RefDataLoaderImpl(
                this,
                byteBufferPool,
                refStreamDefStripedReentrantLock,
                keyValueStoreDb,
                rangeStoreDb,
//                valueStoreDb,
//                valueStoreMetaDb,
                valueStore,
                mapDefinitionUIDStore,
                processingInfoDb,
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
    public long getProcessingInfoEntryCount() {
        return processingInfoDb.getEntryCount();
    }

    /**
     * @param nowMs Allows the setting of the current time for testing purposes
     */
    void purgeOldData(final long nowMs) {
        final Instant startTime = Instant.now();
        final AtomicReference<Tuple4<Integer, Integer, Integer, Integer>> totalsRef = new AtomicReference<>(Tuple.of(0, 0, 0, 0));
        try (final PooledByteBuffer accessTimeThresholdPooledBuf = getAccessTimeCutOffBuffer(nowMs);
             final PooledByteBufferPair procInfoPooledBufferPair = processingInfoDb.getPooledBufferPair()) {

            final AtomicReference<ByteBuffer> currRefStreamDefBufRef = new AtomicReference<>();
            final ByteBuffer accessTimeThresholdBuf = accessTimeThresholdPooledBuf.getByteBuffer();

            Predicate<ByteBuffer> accessTimePredicate = processingInfoBuffer ->
                    !RefDataProcessingInfoSerde.wasAccessedAfter(
                            processingInfoBuffer,
                            accessTimeThresholdBuf);

            final AtomicBoolean wasMatchFound = new AtomicBoolean(false);
            do {
                // with a read txn find the next proc info entry that is ready for purge
                Optional<RefStreamDefinition> optRefStreamDef = LmdbUtils.getWithReadTxn(lmdbEnvironment, readTxn -> {
                    // ensure the buffers are cleared as we are using them in a loop
                    procInfoPooledBufferPair.clear();
                    Optional<PooledByteBufferPair> optProcInfoBufferPair = processingInfoDb.getNextEntryAsBytes(
                            readTxn,
                            currRefStreamDefBufRef.get(),
                            accessTimePredicate,
                            procInfoPooledBufferPair);

                    return optProcInfoBufferPair.map(procInfoBufferPair -> {
                        RefStreamDefinition refStreamDefinition = processingInfoDb.deserializeKey(procInfoBufferPair.getKeyBuffer());

                        // update the current key buffer so we can search from here next time
                        currRefStreamDefBufRef.set(procInfoBufferPair.getKeyBuffer());
                        return refStreamDefinition;
                    });
                });

                if (optRefStreamDef.isPresent()) {

                    LOGGER.debug("Found at least one refStreamDef ready for purge, now getting lock");

                    // now acquire a lock for the this ref stream def so we don't conflict with any load operations
                    doWithRefStreamDefinitionLock(optRefStreamDef.get(), () -> {
                        // start a write txn and re-fetch the next entry for purge (should be the same one as above)
                        // TODO we currently purge a whole refStreamDef in one txn, may be better to do it in smaller
                        // chunks
                        boolean wasFound = LmdbUtils.getWithWriteTxn(lmdbEnvironment, writeTxn -> {

                            final Optional<PooledByteBufferPair> optProcInfoBufferPair =
                                    processingInfoDb.getNextEntryAsBytes(
                                            writeTxn,
                                            currRefStreamDefBufRef.get(),
                                            accessTimePredicate,
                                            procInfoPooledBufferPair);

                            if (!optProcInfoBufferPair.isPresent()) {
                                // no matching ref streams found so break out
                                LOGGER.debug("No match found");
                                return false;
                            } else {
                                // found a ref stream def that is ready for purge
                                final ByteBuffer refStreamDefBuffer = optProcInfoBufferPair.get().getKeyBuffer();
                                final ByteBuffer refDataProcInfoBuffer = optProcInfoBufferPair.get().getValueBuffer();

                                // update this for the next iteration
                                currRefStreamDefBufRef.set(refStreamDefBuffer);

                                final RefStreamDefinition refStreamDefinition = processingInfoDb.deserializeKey(
                                        refStreamDefBuffer);
                                final RefDataProcessingInfo refDataProcessingInfo = processingInfoDb.deserializeValue(
                                        refDataProcInfoBuffer);

                                LOGGER.info("Purging refStreamDefinition {} {}",
                                        refStreamDefinition, refDataProcessingInfo);

                                // mark it is purge in progress
                                processingInfoDb.updateProcessingState(writeTxn,
                                        refStreamDefBuffer,
                                        ProcessingState.PURGE_IN_PROGRESS,
                                        false);

                                // purge the data associated with this ref stream def
                                final Tuple3<Integer, Integer, Integer> refStreamSummaryInfo = purgeRefStreamData(
                                        writeTxn, refStreamDefinition);

                                // aggregate the counts
                                totalsRef.getAndUpdate(totals ->
                                        totals.map((refStreamDefCnt, mapCnt, delCnt, deRefCnt) ->
                                                Tuple.of(refStreamDefCnt + 1,
                                                        mapCnt + refStreamSummaryInfo._1(),
                                                        delCnt + refStreamSummaryInfo._2(),
                                                        deRefCnt + refStreamSummaryInfo._3())));

                                //now delete the proc info entry
                                LOGGER.debug("Deleting processing info entry for {}", refStreamDefinition);

                                boolean didDeleteSucceed = processingInfoDb.delete(writeTxn, refStreamDefBuffer);

                                if (!didDeleteSucceed) {
                                    throw new RuntimeException("Processing info entry not found so was not deleted");
                                }

                                LOGGER.info("Completed purge of refStreamDefinition {} {}",
                                        refStreamDefinition, refDataProcessingInfo);
                                return true;
                            }
                        });
                        wasMatchFound.set(wasFound);
                    });
                } else {
                    wasMatchFound.set(false);
                }
            } while (wasMatchFound.get());
        }

        final Tuple4<Integer, Integer, Integer, Integer> totals = totalsRef.get();
        LOGGER.info("purgeOldData completed in {}, {} refStreamDefs purged, " +
                        "{} maps purged, {} values deleted, {} values de-referenced",
                Duration.between(startTime, Instant.now()),
                totals._1(),
                totals._2(),
                totals._3(),
                totals._4());

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

    private Tuple3<Integer, Integer, Integer> purgeRefStreamData(final Txn<ByteBuffer> writeTxn,
                                                                 final RefStreamDefinition refStreamDefinition) {

        LOGGER.debug("purgeRefStreamData({})", refStreamDefinition);

        Tuple3<Integer, Integer, Integer> summaryInfo = Tuple.of(0, 0, 0);
        int cnt = 0;
        Optional<UID> optMapUid;
        try (PooledByteBuffer pooledUidBuffer = byteBufferPool.getPooledByteBuffer(UID.UID_ARRAY_LENGTH)) {
            do {
                //open a ranged cursor on the map forward table to scan all map defs for that stream def
                //for each map def get the map uid
                optMapUid = mapDefinitionUIDStore.getNextMapDefinition(
                        writeTxn, refStreamDefinition, pooledUidBuffer::getByteBuffer);

                if (optMapUid.isPresent()) {
                    UID mapUid = optMapUid.get();
                    LOGGER.debug("Found mapUid {} for refStreamDefinition {}", mapUid, refStreamDefinition);
                    Tuple2<Integer, Integer> dataPurgeCounts = purgeMapData(writeTxn, optMapUid.get());
                    cnt++;
                    summaryInfo = summaryInfo.map((mapCnt, delCnt, deRefCnt) ->
                            Tuple.of(mapCnt + 1, delCnt + dataPurgeCounts._1(), deRefCnt + dataPurgeCounts._2()));
                } else {
                    LOGGER.debug("No more map definitions to purge for refStreamDefinition {}", refStreamDefinition);
                }
            } while (optMapUid.isPresent());
        }

        LOGGER.info("Purged data for {} map(s) for {}", cnt, refStreamDefinition);
        return summaryInfo;
    }

    private Tuple2<Integer, Integer> purgeMapData(final Txn<ByteBuffer> writeTxn,
                                                  final UID mapUid) {

        LOGGER.debug("purgeMapData(writeTxn, {})", mapUid);

        LOGGER.debug("Deleting key/value entries and de-referencing/deleting their values");
        // loop over all keyValue entries for this mapUid and dereference/delete the associated
        // valueStore entry
        AtomicLong valueEntryDeleteCount = new AtomicLong();
        AtomicLong valueEntryDeReferenceCount = new AtomicLong();
        keyValueStoreDb.deleteMapEntries(writeTxn, mapUid, (keyValueStoreKeyBuffer, valueStoreKeyBuffer) -> {

            //dereference this value, deleting it if required
            deReferenceOrDeleteValue(writeTxn, valueStoreKeyBuffer, valueEntryDeleteCount, valueEntryDeReferenceCount);
        });
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Deleted {} value entries, de-referenced {} value entries",
                valueEntryDeleteCount.get(), valueEntryDeReferenceCount.get()));

        LOGGER.debug("Deleting range/value entries and de-referencing/deleting their values");
        // loop over all rangeValue entries for this mapUid and dereference/delete the associated
        // valueStore entry
        rangeStoreDb.deleteMapEntries(writeTxn, mapUid, (writeTxn2, rangeValueStoreKeyBuffer, valueStoreKeyBuffer) -> {

            //dereference this value, deleting it if required
            deReferenceOrDeleteValue(writeTxn2, valueStoreKeyBuffer, valueEntryDeleteCount, valueEntryDeReferenceCount);
        });
        LOGGER.debug("Deleting range/value entries and de-referencing/deleting their values");

        mapDefinitionUIDStore.deletePair(writeTxn, mapUid);

        return Tuple.of(valueEntryDeleteCount.intValue(), valueEntryDeReferenceCount.intValue());
    }

    private void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                          final ByteBuffer valueStoreKeyBuffer,
                                          final AtomicLong valueEntryDeleteCount,
                                          final AtomicLong valueEntryDeReferenceCount) {

//        boolean wasDeleted = valueStoreMetaDb.deReferenceOrDeleteValue(
//                writeTxn,
//                valueStoreKeyBuffer,
//                ((txn, keyBuffer, valueBuffer) -> valueStoreDb.delete(txn, keyBuffer)));

        boolean wasDeleted = valueStore.deReferenceOrDeleteValue(writeTxn, valueStoreKeyBuffer);

        if (wasDeleted) {
            // we deleted the meta entry so now delete the value entry
            valueEntryDeleteCount.incrementAndGet();
        } else {
            // keep a count of the de-reference
            valueEntryDeReferenceCount.incrementAndGet();
        }
    }

    public void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition, final Runnable work) {
        final Lock lock = refStreamDefStripedReentrantLock.get(refStreamDefinition);

        LAMBDA_LOGGER.logDurationIfDebugEnabled(
                () -> {
                    try {
                        LOGGER.debug("Acquiring lock for {}", refStreamDefinition);
                        lock.lockInterruptibly();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(LambdaLogger.buildMessage(
                                "Thread interrupted while trying to acquire lock for refStreamDefinition {}",
                                refStreamDefinition));
                    }
                },
                () -> LambdaLogger.buildMessage("Acquiring lock for {}", refStreamDefinition));
        try {
            // now we have sole access to this RefStreamDefinition so perform the work on it
            work.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    void logAllContents() {
        logAllContents(LOGGER::debug);
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    void logAllContents(Consumer<String> logEntryConsumer) {
//        processingInfoDb.logDatabaseContents(logEntryConsumer);
//        mapUidForwardDb.logDatabaseContents(logEntryConsumer);
//        mapUidReverseDb.logDatabaseContents(logEntryConsumer);
//        keyValueStoreDb.logDatabaseContents(logEntryConsumer);
//        rangeStoreDb.logDatabaseContents(logEntryConsumer);
//        valueStoreDb.logDatabaseContents(logEntryConsumer);
//        valueReferenceCountDb.logDatabaseContents();
        databaseMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .forEach(lmdbDb -> lmdbDb.logDatabaseContents(logEntryConsumer));
    }

    void logContents(final String dbName) {
        doWithLmdb(dbName, LmdbDb::logDatabaseContents);
    }

    void doWithLmdb(final String dbName, final Consumer<LmdbDb> work) {
        LmdbDb lmdbDb = databaseMap.get(dbName);
        if (lmdbDb == null) {
            throw new IllegalArgumentException(LambdaLogger.buildMessage("No database with name {} exists", dbName));
        }
        work.accept(lmdbDb);
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    void logAllRawContents(Consumer<String> logEntryConsumer) {
//        processingInfoDb.logRawDatabaseContents();
//        mapUidForwardDb.logRawDatabaseContents();
//        mapUidReverseDb.logRawDatabaseContents();
//        keyValueStoreDb.logRawDatabaseContents();
//        rangeStoreDb.logRawDatabaseContents();
//        valueStoreDb.logRawDatabaseContents();
//        valueReferenceCountDb.logRawDatabaseContents();
        databaseMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .forEach(lmdbDb -> lmdbDb.logDatabaseContents(logEntryConsumer));
    }

    void logRawContents(final String dbName) {
        doWithLmdb(dbName, LmdbDb::logRawDatabaseContents);
    }

    long getEntryCount(final String dbName) {
        LmdbDb lmdbDb = databaseMap.get(dbName);
        if (lmdbDb == null) {
            throw new IllegalArgumentException(LambdaLogger.buildMessage("No database with name {} exists", dbName));
        }
        return lmdbDb.getEntryCount();
    }

    private String getDataRetentionAgeString() {
        return stroomPropertyService.getProperty(DATA_RETENTION_AGE_PROP_KEY, DATA_RETENTION_AGE_DEFAULT_VALUE);
    }

    private PooledByteBuffer getAccessTimeCutOffBuffer(final long nowMs) {
        long purgeAge = ModelStringUtil.parseDurationString(getDataRetentionAgeString());
        long purgeCutOff = nowMs - purgeAge;

        LOGGER.info("Using purge duration {}, cut off {}, now {}",
                Duration.ofMillis(purgeAge),
                Instant.ofEpochMilli(purgeCutOff),
                Instant.ofEpochMilli(nowMs));

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(Long.BYTES);
        pooledByteBuffer.getByteBuffer().putLong(purgeCutOff);
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    @Override
    public HealthCheck.Result getHealth() {

        try {
            Tuple2<Instant, Instant> lastAccessedTimeRange = processingInfoDb.getLastAccessedTimeRange();
            HealthCheck.ResultBuilder builder = HealthCheck.Result.builder();
            builder
                    .healthy()
                    .withDetail("Path", dbDir.toAbsolutePath().toString())
                    .withDetail("Environment max size", ModelStringUtil.formatIECByteSizeString(maxSize))
                    .withDetail("Environment current size", ModelStringUtil.formatIECByteSizeString(getEnvironmentDiskUsage()))
                    .withDetail("Purge age", getDataRetentionAgeString())
                    .withDetail("Max readers", maxReaders)
                    .withDetail("Current buffer pool size", byteBufferPool.getCurrentPoolSize())
                    .withDetail("Earliest lastAccessedTime", lastAccessedTimeRange._1().toString())
                    .withDetail("Latest lastAccessedTime", lastAccessedTimeRange._2().toString());

            LmdbUtils.doWithReadTxn(lmdbEnvironment, txn -> {
                builder.withDetail("Database entry counts", databaseMap.entrySet().stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(
                                Map.Entry::getKey,
                                entry -> entry.getValue().getEntryCount(txn))));
            });
            return builder.build();
        } catch (RuntimeException e) {
            return HealthCheck.Result.builder()
                    .unhealthy(e)
                    .build();
        }
    }

    private long getEnvironmentDiskUsage() {
        long totalSizeBytes;
        try (final Stream<Path> fileStream = Files.list(dbDir)) {
            totalSizeBytes = fileStream
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Error calculating disk usage for path {}", dbDir.toAbsolutePath().toString(), e);
            totalSizeBytes = -1;
        }
        return totalSizeBytes;
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * Class for adding multiple items to the {@link RefDataOffHeapStore} within a single
     * write transaction. Accessed via
     * {@link RefDataStore#doWithLoaderUnlessComplete(RefStreamDefinition, long, Consumer)}.
     * Commits data loaded so far every N entries, where N is maxPutsBeforeCommit.
     * If a value of maxPutsBeforeCommit is greater than one then processing should be kept
     * as lightweight as possible to avoid holding on to a write txn for too long.
     * The transaction will be committed when the loader is closed.
     * The loader instance is NOT thread safe so must be used by a single thread.
     */
    public static class RefDataLoaderImpl implements RefDataLoader {

        private Txn<ByteBuffer> writeTxn = null;
        private final RefDataOffHeapStore refDataOffHeapStore;
        private final ByteBufferPool byteBufferPool;
        private final Lock refStreamDefReentrantLock;

        private final KeyValueStoreDb keyValueStoreDb;
        private final RangeStoreDb rangeStoreDb;
        private final ValueStore valueStore;
        private final MapDefinitionUIDStore mapDefinitionUIDStore;
        private final ProcessingInfoDb processingInfoDb;

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

        private final PooledByteBuffer keyValuePooledKeyBuffer;
        private final PooledByteBuffer rangeValuePooledKeyBuffer;
        private final PooledByteBuffer valueStorePooledKeyBuffer;

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
                                  final ValueStore valueStore,
                                  final MapDefinitionUIDStore mapDefinitionUIDStore,
                                  final ProcessingInfoDb processingInfoDb,
                                  final Env<ByteBuffer> lmdbEnvironment,
                                  final RefStreamDefinition refStreamDefinition,
                                  final long effectiveTimeMs) {

            this.refDataOffHeapStore = refDataOffHeapStore;
            this.byteBufferPool = byteBufferPool;
            this.keyValueStoreDb = keyValueStoreDb;
            this.rangeStoreDb = rangeStoreDb;
            this.processingInfoDb = processingInfoDb;

            this.valueStore = valueStore;
            this.mapDefinitionUIDStore = mapDefinitionUIDStore;
            this.lmdbEnvironment = lmdbEnvironment;
            this.refStreamDefinition = refStreamDefinition;
            this.effectiveTimeMs = effectiveTimeMs;

            // get buffers to (re)use for the life of the loader
            this.keyValuePooledKeyBuffer = keyValueStoreDb.getPooledKeyBuffer();
            this.rangeValuePooledKeyBuffer = rangeStoreDb.getPooledKeyBuffer();
            this.valueStorePooledKeyBuffer = valueStore.getPooledKeyBuffer();

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

            LOGGER.debug("initialise called, overwriteExisting: {}", overwriteExisting);
            checkCurrentState(LoaderState.NEW);

            startTime = Instant.now();

            this.overwriteExisting = overwriteExisting;

            final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    effectiveTimeMs,
                    ProcessingState.LOAD_IN_PROGRESS);

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
            LOGGER.trace("Using mapUid {} for {}", mapUid, mapDefinition);
            final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(mapUid, key);

            final ByteBuffer keyValueKeyBuffer = keyValuePooledKeyBuffer.getByteBuffer();
            // ensure the buffer is clear as we are reusing the same one for each put
            keyValueKeyBuffer.clear();
            keyValueStoreDb.serializeKey(keyValueKeyBuffer, keyValueStoreKey);
            Optional<ByteBuffer> optCurrValueStoreKeyBuffer = keyValueStoreDb.getAsBytes(
                    writeTxn, keyValueKeyBuffer);

            // TODO May be able to create direct buffer for reuse over all write ops to save the cost
            // of buffer allocation, see info here https://github.com/lmdbjava/lmdbjava/issues/81
            // If the loader holds a key and value ByteBuffer then they can be used for all write ops
            // See TestByteBufferReusePerformance

            // see if we have a value already for this key
            // if overwrite == false, we can just drop out here
            // if overwrite == true we need to de-reference the value (and maybe delete)
            // then create a new value, assuming they are different
            boolean didPutSucceed;
            if (optCurrValueStoreKeyBuffer.isPresent()) {
                if (!overwriteExisting) {
                    // already have an entry for this key so drop out here
                    // with nothing to do as we can't overwrite anything
                    didPutSucceed = false;
                } else {
                    // overwriting and we already have a value so see if the old and
                    // new values are the same.
                    ByteBuffer currValueStoreKeyBuffer = optCurrValueStoreKeyBuffer.get();
//                    ValueStoreKey currentValueStoreKey = optCurrentValueStoreKey.get();

//                    RefDataValue currentValueStoreValue = valueStoreDb.get(writeTxn, currentValueStoreKey)
//                            .orElseThrow(() -> new RuntimeException("Should have a value at this point"));

                    boolean areValuesEqual = valueStore.areValuesEqual(
                            writeTxn, currValueStoreKeyBuffer, refDataValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        didPutSucceed = true;
                    } else {
                        // value is different so we need to de-reference the old one
                        // and getOrCreate the new one
//                        valueStoreDb.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);
                        valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

                        didPutSucceed = createKeyValue(refDataValue, keyValueKeyBuffer);
                    }
                }
            } else {
                // no existing valueStoreKey so create the entries
                didPutSucceed = createKeyValue(refDataValue, keyValueKeyBuffer);
            }

            if (didPutSucceed) {
                successfulPutsCounter++;
            }

            commitIfRequired();

            keyValuePooledKeyBuffer.clear();

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
            LOGGER.trace("Using mapUid {} for {}", mapUid, mapDefinition);
            final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, keyRange);

            // see if we have a value already for this key
            // if overwrite == false, we can just drop out here
            // if overwrite == true we need to de-reference the value (and maybe delete)
            // then create a new value, assuming they are different
            final ByteBuffer rangeValueKeyBuffer = rangeValuePooledKeyBuffer.getByteBuffer();
            // ensure the buffer is clear as we are reusing the same one for each put
            rangeValueKeyBuffer.clear();
            rangeStoreDb.serializeKey(rangeValueKeyBuffer, rangeStoreKey);

            Optional<ByteBuffer> optCurrValueStoreKeyBuffer = rangeStoreDb.getAsBytes(
                    writeTxn, rangeValueKeyBuffer);

            boolean didPutSucceed;
            if (optCurrValueStoreKeyBuffer.isPresent()) {
                if (!overwriteExisting) {
                    // already have an entry for this key so drop out here
                    // with nothing to do as we can't overwrite anything
                    didPutSucceed = false;
                } else {
                    // overwriting and we already have a value so see if the old and
                    // new values are the same.
                    ByteBuffer currValueStoreKeyBuffer = optCurrValueStoreKeyBuffer.get();
//                    ValueStoreKey currentValueStoreKey = optCurrValueStoreKeyBuffer.get();

                    boolean areValuesEqual = valueStore.areValuesEqual(
                            writeTxn, currValueStoreKeyBuffer, refDataValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        didPutSucceed = true;
                    } else {
                        // value is different so we need to de-reference the old one
                        // and getOrCreate the new one
                        valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

//                        final ByteBuffer valueStoreKeyBuffer = valueStoreDb.getOrCreate(
//                                writeTxn, refDataValue, valueStorePooledKeyBuffer, overwriteExisting);
                        //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                        didPutSucceed = createRangeValue(refDataValue, rangeValueKeyBuffer);
                    }
                }
            } else {
                // no existing valueStoreKey so create the entries
                didPutSucceed = createRangeValue(refDataValue, rangeValueKeyBuffer);
            }

            if (didPutSucceed) {
                successfulPutsCounter++;
            }
            commitIfRequired();
            rangeValuePooledKeyBuffer.clear();
            return didPutSucceed;
        }


        private boolean createKeyValue(final RefDataValue refDataValue, final ByteBuffer keyValueKeyBuffer) {

            final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                    writeTxn, valueStorePooledKeyBuffer, refDataValue, overwriteExisting);

            // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
            // do a get then optional put.
            return keyValueStoreDb.put(
                    writeTxn, keyValueKeyBuffer, valueStoreKeyBuffer, overwriteExisting);
        }

        private boolean createRangeValue(final RefDataValue refDataValue, final ByteBuffer rangeValueKeyBuffer) {

            final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                    writeTxn, valueStorePooledKeyBuffer, refDataValue, overwriteExisting);

            // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
            // do a get then optional put.
            return rangeStoreDb.put(
                    writeTxn, rangeValueKeyBuffer, valueStoreKeyBuffer, overwriteExisting);
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
            // release our pooled buffers back to the pool
            keyValuePooledKeyBuffer.release();
            rangeValuePooledKeyBuffer.release();
            valueStorePooledKeyBuffer.release();

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
                return mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDef)
                        .clone();
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
