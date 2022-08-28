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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.docstore.shared.DocRefUtil;
import stroom.lmdb.LmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.LmdbEnvFactory;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.store.AbstractRefDataStore;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.ProcessingInfoResponse.EntryCounts;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueConverter;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb.Factory;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import com.google.common.util.concurrent.Striped;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

/**
 * An Off Heap implementation of {@link RefDataStore} using LMDB.
 * <p>
 * Essentially each ref stream (a {@link RefStreamDefinition}) contains 1-* map names.
 * Multiple ref streams can contain the same map name.
 * Within a ref stream + map combo (a {@link MapDefinition}) there are 1-* entries.
 * A lookup is done by creating a key that is the lookup key combined with a {@link MapDefinition}
 * so only an entry matching the lookup key, the map name and the ref stream will be returned.
 */
@Singleton
public class RefDataOffHeapStore extends AbstractRefDataStore implements RefDataStore, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    private static final String DEFAULT_STORE_SUB_DIR_NAME = "refDataOffHeapStore";

    private static final TemporalUnit PROCESSING_INFO_TRUNCATION_UNIT = ChronoUnit.HOURS;

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    private static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    private static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";

    private final LmdbEnvFactory lmdbEnvFactory;
    private final TaskContext taskContext;

    private final LmdbEnv lmdbEnvironment;

    // the DBs that make up the store
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ProcessingInfoDb processingInfoDb;

    // classes that front multiple DBs
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;

    private final Provider<ReferenceDataConfig> referenceDataConfigProvider;

    private final RefDataValueConverter refDataValueConverter;

    private final Map<String, LmdbDb> databaseMap = new HashMap<>();

    // For synchronising access to the data belonging to a MapDefinition
    private final Striped<Lock> refStreamDefStripedReentrantLock;

    private final ByteBufferPool byteBufferPool;

    @Inject
    RefDataOffHeapStore(
            final LmdbEnvFactory lmdbEnvFactory,
            final Provider<ReferenceDataConfig> referenceDataConfigProvider,
            final ByteBufferPool byteBufferPool,
            final Factory keyValueStoreDbFactory,
            final ValueStoreDb.Factory valueStoreDbFactory,
            final ValueStoreMetaDb.Factory valueStoreMetaDbFactory,
            final RangeStoreDb.Factory rangeStoreDbFactory,
            final MapUidForwardDb.Factory mapUidForwardDbFactory,
            final MapUidReverseDb.Factory mapUidReverseDbFactory,
            final RefDataValueConverter refDataValueConverter,
            final ProcessingInfoDb.Factory processingInfoDbFactory,
            final TaskContext taskContext) {

        this.lmdbEnvFactory = lmdbEnvFactory;
        this.referenceDataConfigProvider = referenceDataConfigProvider;
        this.refDataValueConverter = refDataValueConverter;
        this.taskContext = taskContext;

        this.lmdbEnvironment = createEnvironment(referenceDataConfigProvider.get().getLmdbConfig());

        // create all the databases
        this.keyValueStoreDb = keyValueStoreDbFactory.create(lmdbEnvironment);
        this.rangeStoreDb = rangeStoreDbFactory.create(lmdbEnvironment);
        final ValueStoreDb valueStoreDb = valueStoreDbFactory.create(lmdbEnvironment);
        final MapUidForwardDb mapUidForwardDb = mapUidForwardDbFactory.create(lmdbEnvironment);
        final MapUidReverseDb mapUidReverseDb = mapUidReverseDbFactory.create(lmdbEnvironment);
        this.processingInfoDb = processingInfoDbFactory.create(lmdbEnvironment);
        final ValueStoreMetaDb valueStoreMetaDb = valueStoreMetaDbFactory.create(lmdbEnvironment);

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
        this.mapDefinitionUIDStore = new MapDefinitionUIDStore(
                lmdbEnvironment,
                mapUidForwardDb,
                mapUidReverseDb);

        this.byteBufferPool = byteBufferPool;

        // Need a reasonable number to try and avoid keys that are not equal from using the
        // same stripe
        final int stripesCount = referenceDataConfigProvider.get().getLoadingLockStripes();
        LOGGER.debug("Initialising striped with {} stripes", stripesCount);
        this.refStreamDefStripedReentrantLock = Striped.lazyWeakLock(stripesCount);
    }

    private void purgePartialLoads() {

        final Instant startTime = Instant.now();

        final Predicate<ByteBuffer> partialLoadProgressPredicate =
                RefDataProcessingInfoSerde.createProcessingStatePredicate(
                        ProcessingState.LOAD_IN_PROGRESS,
                        ProcessingState.PURGE_IN_PROGRESS,
                        ProcessingState.TERMINATED);

        // Get all the ref stream defs in a partially loaded/purged state
        // It is only in exceptional circumstances that we get streams in this state so the list should
        // not be big.
        final List<Entry<RefStreamDefinition, RefDataProcessingInfo>> invalidStreams = lmdbEnvironment.getWithReadTxn(
                readTxn -> getInvalidStreams(readTxn, partialLoadProgressPredicate));

        if (!invalidStreams.isEmpty()) {
            AtomicReference<PurgeCounts> purgeCountsRef = new AtomicReference<>(PurgeCounts.ZERO);
            for (final Entry<RefStreamDefinition, RefDataProcessingInfo> entry : invalidStreams) {
                try (final PooledByteBuffer refStreamDefPooledBuf = processingInfoDb.getPooledKeyBuffer()) {
                    final RefStreamDefinition refStreamDefinition = entry.getKey();
                    final RefDataProcessingInfo refDataProcessingInfo = entry.getValue();
                    this.taskContext.info(() -> LogUtil.message(
                            "Purging partially loaded/purged reference stream {}:{} with state {}",
                            refStreamDefinition.getStreamId(),
                            refStreamDefinition.getPartNumber(),
                            refDataProcessingInfo.getProcessingState()));
                    processingInfoDb.serializeKey(refStreamDefPooledBuf.getByteBuffer(), refStreamDefinition);

                    // Need to recheck the state under lock in case it has changed
                    final RefStreamPurgeCounts refStreamPurgeCounts = purgeRefStreamIfEligible(
                            partialLoadProgressPredicate,
                            refStreamDefPooledBuf.getByteBuffer(),
                            refStreamDefinition);

                    purgeCountsRef.set(purgeCountsRef.get().increment(refStreamPurgeCounts));
                }
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.debug("Thread interrupted during ");
                }
            }

            LOGGER.info(() -> LogUtil.message(
                    "Purge of partial loads/purges {}- {}",
                    (Thread.currentThread().isInterrupted()
                            ? "interrupted"
                            : "completed successfully "),
                    buildPurgeInfoString(startTime, purgeCountsRef.get())));
        } else {
            LOGGER.info("Completed check for invalid ref loads/purges in {}",
                    Duration.between(startTime, Instant.now()));
        }
    }

    private List<Entry<RefStreamDefinition, RefDataProcessingInfo>> getInvalidStreams(
            final Txn<ByteBuffer> writeTxn,
            final Predicate<ByteBuffer> partialLoadProgressPredicate) {

        final List<Entry<RefStreamDefinition, RefDataProcessingInfo>> invalidStreams =
                processingInfoDb.streamEntriesAsBytes(
                        writeTxn,
                        KeyRange.all(),
                        stream ->
                                stream.filter(keyVal ->
                                                partialLoadProgressPredicate.test(keyVal.val()))
                                        .map(processingInfoDb::deserializeKeyVal)
                                        .collect(Collectors.toList()));

        final Map<ProcessingState, Long> countsByState = invalidStreams.stream()
                .collect(Collectors.groupingBy(entry ->
                        entry.getValue().getProcessingState(), Collectors.counting()));

        LOGGER.info("Found partially loaded/purged ref streams (counts: {}) (total {}). " +
                        "They will now all be purged.",
                countsByState,
                invalidStreams.size());

        return invalidStreams;
    }

    private LmdbEnv createEnvironment(final ReferenceDataLmdbConfig lmdbConfig) {

        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.

        // NOTE on setMapSize() from LMDB author found on https://groups.google.com/forum/#!topic/caffe-users/0RKsTTYRGpQ
        // On Windows the OS sets the filesize equal to the mapsize. (MacOS requires that too, and allocates
        // all of the physical space up front, it doesn't support sparse files.) The mapsize should not be
        // hardcoded into software, it needs to be reconfigurable. On Windows and MacOS you really shouldn't
        // set it larger than the amount of free space on the filesystem.

        final LmdbEnv env = lmdbEnvFactory.builder(lmdbConfig)
                .withMaxDbCount(7)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        LOGGER.info("Existing databases: [{}]", String.join(",", env.getDbiNames()));
        return env;
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.OFF_HEAP;
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
            // Truncate the last access time so it is clear to anyone looking at the values that
            // they are approx.

            final RefDataProcessingInfo updatedProcessingInfo = processingInfo.updateLastAccessedTime();

            if (!processingInfo.getLastAccessedTime().equals(updatedProcessingInfo.getLastAccessedTime())) {
                processingInfoDb.updateLastAccessedTime(
                        refStreamDefinition, updatedProcessingInfo.getLastAccessedTimeEpochMs());
            }
        });
        LOGGER.trace("getProcessingInfo({}) - {}", refStreamDefinition, optProcessingInfo);
        return optProcessingInfo;
    }

    @Override
    public Optional<ProcessingState> getLoadState(final RefStreamDefinition refStreamDefinition) {
        return getAndTouchProcessingInfo(refStreamDefinition)
                .map(RefDataProcessingInfo::getProcessingState);
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
                    lmdbEnvironment.getWithReadTxn(readTxn ->
                            // Perform the lookup with the map+key. The returned value (if found)
                            // is the key of the ValueStore, which we can use to find the actual
                            // value.
                            getValueStoreKey(readTxn, mapDefinition, key)
                                    .flatMap(valueStoreKeyBuffer -> {
                                        // we are going to use the valueStoreKeyBuffer as a key in multiple
                                        // get() calls so need to clone it first.
                                        final ByteBuffer valueStoreKeyBufferClone =
                                                valueStoreKeyPooledBufferClone.getByteBuffer();
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
    public Set<String> getMapNames(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        return lmdbEnvironment.getWithReadTxn(readTxn ->
                mapDefinitionUIDStore.getMapNames(readTxn, refStreamDefinition));
    }

    /**
     * Intended only for testing use.
     */
    void setLastAccessedTime(final RefStreamDefinition refStreamDefinition, long timeMs) {
        processingInfoDb.updateLastAccessedTime(refStreamDefinition, timeMs);
    }

    /**
     * Intended only for testing use.
     */
    void setProcessingState(final RefStreamDefinition refStreamDefinition, ProcessingState processingState) {
        lmdbEnvironment.doWithWriteTxn(writeTxn -> {
            processingInfoDb.updateProcessingState(
                    writeTxn,
                    refStreamDefinition,
                    processingState,
                    true);
        });
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

            if (optValueStoreKeyBuffer.isEmpty()) {
                //not found in the kv store so look in the key range store instead

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
                    // TODO @AT Could maybe hold the result in a short lived on-heap cache to improve performance
                    boolean doesStoreContainRanges = rangeStoreDb.containsMapDefinition(readTxn, mapUid);
                    if (doesStoreContainRanges) {
                        // we have ranges for this map def so we would expect to be able to convert the key
                        throw new RuntimeException(LogUtil.message(
                                "Key {} cannot be used with the range store as it cannot be converted to a long", key),
                                e);
                    }
                    // no ranges for this map def so the fact that we could not convert the key to a long
                    // is not a problem. Do nothing.
                }
            }
        } else {
            LOGGER.debug("Couldn't find map UID which means the data for this map has " +
                            "not been loaded or the map name is wrong {}",
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
            boolean wasValueFound = lmdbEnvironment.getWithReadTxn(txn ->
                    getValueStoreKey(txn, mapDefinition, key)
                            .flatMap(valueStoreKeyBuffer -> {
                                // we are going to use the valueStoreKeyBuffer as a key in multiple
                                // get() calls so need to clone it first.
                                final ByteBuffer valueStoreKeyBufferClone =
                                        valueStoreKeyPooledBufferClone.getByteBuffer();
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

    @Override
    public void purgeOldData() {
        LOGGER.info("purgeAge in purgeOldData: {}", referenceDataConfigProvider.get().getPurgeAge());
        purgeOldData(Instant.now(), referenceDataConfigProvider.get().getPurgeAge());

        // Also clear out any partial loads/purges
        purgePartialLoads();
    }

    @Override
    public void purgeOldData(final StroomDuration purgeAge) {
        purgeOldData(Instant.now(), purgeAge);

        // Also clear out any partial loads/purges
        purgePartialLoads();
    }

    @Override
    public void purge(final long refStreamId, final long partIndex) {

        final Instant startTime = Instant.now();
        this.taskContext.info(() -> LogUtil.message("Purging data for reference stream {}:{}",
                refStreamId, partIndex));

        final AtomicReference<PurgeCounts> countsRef = new AtomicReference<>(PurgeCounts.zero());


        try (final PooledByteBuffer refStreamDefPooledBuf = processingInfoDb.getPooledKeyBuffer()) {

            final Predicate<RefStreamDefinition> refStreamDefinitionPredicate = refStreamDef ->
                    refStreamDef.getStreamId() == refStreamId && refStreamDef.getPartIndex() == partIndex;

            // Initially we scan the whole range but as we find stuff we move the start key
            final AtomicReference<KeyRange<RefStreamDefinition>> keyRangeRef = new AtomicReference<>(KeyRange.all());
            boolean wasMatchFound;
            do {
                // Allow for task termination
                if (Thread.currentThread().isInterrupted() || taskContext.isTerminated()) {
                    // As we are outside of a txn the interruption is ok and everything will be in
                    // valid state for when purge is run again. Thus we don't n
                    throw new TaskTerminatedException();
                }

                // Find the next one that is ready for purge
                // Can just use a stream and for each as that would result in a cursor inside a cursor
                final Optional<Entry<RefStreamDefinition, RefDataProcessingInfo>> optEntry =
                        lmdbEnvironment.getWithReadTxn(readTxn ->
                                processingInfoDb.findFirstMatchingKey(
                                        readTxn,
                                        keyRangeRef.get(),
                                        refStreamDefinitionPredicate));

                if (optEntry.isPresent()) {
                    wasMatchFound = true;
                    final RefStreamDefinition refStreamDefinition = optEntry.get().getKey();
                    LOGGER.debug("refStreamDefinition: {}", refStreamDefinition);

                    // Make the next iteration start just after this entry
                    keyRangeRef.set(KeyRange.greaterThan(refStreamDefinition));
                    processingInfoDb.serializeKey(refStreamDefPooledBuf.getByteBuffer(), refStreamDefinition);

                    // Always true predicate as there is no condition on the processing info
                    final RefStreamPurgeCounts refStreamPurgeCounts = purgeRefStreamIfEligible(
                            byteBuffer -> true,
                            refStreamDefPooledBuf.getByteBuffer(),
                            refStreamDefinition);

                    // aggregate the counts
                    countsRef.getAndUpdate(counts ->
                            counts.increment(refStreamPurgeCounts));
                } else {
                    LOGGER.debug("No matching ref stream found");
                    wasMatchFound = false;
                }
            } while (wasMatchFound);

            final PurgeCounts purgeCounts = countsRef.get();

            LOGGER.debug("purgeCounts: {}", purgeCounts);

            if (purgeCounts.refStreamDefsFailedCount > 0) {
                // One or more ref stream defs failed
                throw new RuntimeException(LogUtil.message(
                        "Unable to purge {} ref stream definitions",
                        purgeCounts.refStreamDefsFailedCount));
            } else if (purgeCounts.refStreamDefsDeletedCount > 0) {
                LOGGER.info(() -> "Purge completed successfully - " +
                        buildPurgeInfoString(startTime, purgeCounts));
            } else {
                LOGGER.info(() -> "Purge completed with no data to purge.");
            }
        } catch (TaskTerminatedException e) {
            // Expected behaviour so just rethrow, stopping it being picked up by the other
            // catch block
            LOGGER.debug("Purge terminated", e);
            LOGGER.warn(() -> "Purge terminated - " +
                    buildPurgeInfoString(startTime, countsRef.get()));
            throw e;
        } catch (Exception e) {
            LOGGER.error(() -> "Purge failed due to " + e.getMessage() + ". " +
                    buildPurgeInfoString(startTime, countsRef.get()), e);
            throw e;
        }
    }

    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     */
    @Override
    protected RefDataLoader loader(final RefStreamDefinition refStreamDefinition,
                                   final long effectiveTimeMs) {
        //TODO should we pass in an ErrorReceivingProxy so we can log errors with it?
        RefDataLoader refDataLoader = new OffHeapRefDataLoader(
                this,
                refStreamDefStripedReentrantLock,
                keyValueStoreDb,
                rangeStoreDb,
                valueStore,
                mapDefinitionUIDStore,
                processingInfoDb,
                lmdbEnvironment,
                refStreamDefinition,
                effectiveTimeMs);

        refDataLoader.setCommitInterval(referenceDataConfigProvider.get().getMaxPutsBeforeCommit());
        return refDataLoader;
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
     * Synchronized to prevent to ensure concurrent purge jobs don't clash
     *
     * @param now Allows the setting of the current time for testing purposes
     */
    synchronized void purgeOldData(final Instant now, final StroomDuration purgeAge) {
        taskContext.info(() -> "Purging old data");
        final Instant startTime = Instant.now();
        final AtomicReference<PurgeCounts> countsRef = new AtomicReference<>(PurgeCounts.zero());

        final Instant purgeCutOffTime = TimeUtils.durationToThreshold(now, purgeAge);

        LOGGER.info("Purging reference data store with purge age {} ({}), cut off time {}",
                purgeAge,
                purgeAge.getDuration(),
                purgeCutOffTime);

        try (final PooledByteBuffer accessTimeThresholdPooledBuf = getAccessTimeCutOffBuffer(purgeCutOffTime);
                final PooledByteBufferPair procInfoPooledBufferPair = processingInfoDb.getPooledBufferPair()) {

            // Reference is initially empty so we will scan from the beginning of the DB
            final AtomicReference<ByteBuffer> currRefStreamDefBufRef = new AtomicReference<>();
            final ByteBuffer accessTimeThresholdBuf = accessTimeThresholdPooledBuf.getByteBuffer();

            final Predicate<ByteBuffer> accessTimePredicate = processingInfoBuffer ->
                    !RefDataProcessingInfoSerde.wasAccessedAfter(
                            processingInfoBuffer,
                            accessTimeThresholdBuf);

            boolean wasMatchFound;
            do {
                // Allow for task termination
                if (Thread.currentThread().isInterrupted()) {
                    // As we are outside of a txn the interruption is ok and everything will be in
                    // valid state for when purge is run again.
                    throw new TaskTerminatedException();
                }

                // With a read txn scan over all the proc info entries to find the next one that is ready for purge
                final Optional<RefStreamDefinition> optRefStreamDef = lmdbEnvironment.getWithReadTxn(readTxn ->
                        findNextRefStreamDef(
                                procInfoPooledBufferPair,
                                currRefStreamDefBufRef,
                                accessTimePredicate,
                                readTxn));

                if (optRefStreamDef.isPresent()) {
                    wasMatchFound = true;
                    final RefStreamDefinition refStreamDefinition = optRefStreamDef.get();

                    final RefStreamPurgeCounts refStreamPurgeCounts = purgeRefStreamIfEligible(
                            accessTimePredicate,
                            currRefStreamDefBufRef.get(),
                            refStreamDefinition);

                    // aggregate the counts
                    countsRef.getAndUpdate(counts ->
                            counts.increment(refStreamPurgeCounts));
                } else {
                    LOGGER.debug("No matching ref stream found");
                    wasMatchFound = false;
                }

                // Sleep block to slow things down for testing
//                try {
//                    Thread.sleep(20_000);
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
            } while (wasMatchFound);

            final PurgeCounts purgeCounts = countsRef.get();
            if (purgeCounts.refStreamDefsFailedCount == 0) {
                LOGGER.info(() -> "Purge completed successfully - " +
                        buildPurgeInfoString(startTime, purgeCounts));
            } else {
                // One or more ref stream defs failed
                throw new RuntimeException(LogUtil.message(
                        "Unable to purge {} ref stream definitions",
                        purgeCounts.refStreamDefsFailedCount));
            }
        } catch (TaskTerminatedException e) {
            // Expected behaviour so just rethrow, stopping it being picked up by the other
            // catch block
            LOGGER.debug("Purge terminated", e);
            LOGGER.warn(() -> "Purge terminated - " +
                    buildPurgeInfoString(startTime, countsRef.get()));
            throw e;
        } catch (Exception e) {
            LOGGER.error(() -> "Purge failed due to " + e.getMessage() + ". " +
                    buildPurgeInfoString(startTime, countsRef.get()), e);
            throw e;
        }

        //open a write txn
        //open a cursor on the process info table to scan all records
        //subtract purge age prop val from current time to give purge cut off ms
        //for each proc info record one test the last access time against the cut off time (without
        // de-serialising to long)
        //if it is older than cut off date then change its state to PURGE_IN_PROGRESS


        //process needs to be idempotent so we can continue a part finished purge. A new txn MUST always check the
        //processing info state to ensure it is still PURGE_IN_PROGRESS in case another txn has started a load, in which
        //case we won't purge. A purge txn must wrap at least the deletion of the key(range)/entry, the value
        // (if no other
        //refs). The deletion of the mapdef<=>uid paiur must be done in a txn to ensure consistency.
        //Each processing info entry should be be fetched with a read txn, then get a StripedSemaphore for the
        // streamdef
        //then open the write txn. This should stop any conflict with load jobs for that stream.

        //when overwrites happen we may have two values that had an association with same mapDef + key.  The
        // above process
        //will only remove the currently associated value.  We would have to scan the whole value table to look for


        // streamDef => mapDefs
        // mapDef => mapUID
        // mapUID => ValueKeys
        // ValueKey => value

        // <pipe uuid 2-18><pipe ver 2-18><stream id 8> => <create time 8><last access time 8><effective time 8><state1>
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

        // change to ref counter MUST be done in same txn as the thing that is making it change,
        // e.g the KV entry removal
    }

    private String buildPurgeInfoString(final Instant startTime,
                                        final PurgeCounts purgeCounts) {
        return LogUtil.message("refStreamDefs purged: {}, refStreamDef purge failures: {}, " +
                        "maps deleted: {}, values deleted: {}, values de-referenced: {}. " +
                        "Time taken {}",
                purgeCounts.refStreamDefsDeletedCount,
                purgeCounts.refStreamDefsFailedCount,
                purgeCounts.refStreamPurgeCounts.mapsDeletedCount,
                purgeCounts.refStreamPurgeCounts.valuesDeletedCount,
                purgeCounts.refStreamPurgeCounts.valuesDeReferencedCount,
                Duration.between(startTime, Instant.now()));
    }

    private RefStreamPurgeCounts purgeRefStreamIfEligible(
            final Predicate<ByteBuffer> processingInfoBufferPredicate,
            final ByteBuffer refStreamDefinitionBuf,
            final RefStreamDefinition refStreamDefinition) {

        LOGGER.debug("Attempting to purge ref stream {} if eligible", refStreamDefinition);
        final AtomicReference<RefStreamPurgeCounts> refStreamPurgeCountsRef = new AtomicReference<>();

        taskContext.info(() -> "Acquiring lock for reference stream " +
                refStreamDefinition.getStreamId() + ":" + refStreamDefinition.getPartIndex());

        // now acquire a lock for this ref stream def, so we don't conflict with any load operations
        doWithRefStreamDefinitionLock(refStreamDefStripedReentrantLock, refStreamDefinition, () -> {

            try {
                try (final BatchingWriteTxn batchingWriteTxn = lmdbEnvironment.openBatchingWriteTxn(
                        referenceDataConfigProvider.get().getMaxPurgeDeletesBeforeCommit())) {

                    // We now hold an open write txn so re-fetch the processing info in case something has
                    // changed between our first read and now
                    final Optional<ByteBuffer> optRefDataProcInfoBuf = processingInfoDb.getAsBytes(
                            batchingWriteTxn.getTxn(), refStreamDefinitionBuf);

                    if (optRefDataProcInfoBuf.isPresent()) {
                        final ByteBuffer refDataProcessingInfoBuffer = optRefDataProcInfoBuf.get();

                        LOGGER.debug(() -> LogUtil.message("refStreamDefinition: {}",
                                processingInfoDb.deserializeKey(refDataProcessingInfoBuffer)));

                        // Test the predicate to ensure we can purge it
                        if (processingInfoBufferPredicate.test(refDataProcessingInfoBuffer)) {

                            final RefDataProcessingInfo refDataProcessingInfo = processingInfoDb.deserializeValue(
                                    refDataProcessingInfoBuffer);

                            final RefStreamPurgeCounts refStreamPurgeCounts = purgeRefStreamDef(
                                    refStreamDefinition,
                                    refDataProcessingInfo,
                                    refStreamDefinitionBuf,
                                    batchingWriteTxn);

                            refStreamPurgeCountsRef.set(refStreamPurgeCounts);
                        } else {
                            LOGGER.debug(() -> LogUtil.message("Ref stream {} not eligible for purge, info {}",
                                    refStreamDefinition,
                                    processingInfoDb.deserializeValue(refDataProcessingInfoBuffer)));
                        }
                    } else {
                        LOGGER.info("Ref data processing info does not exist, another thread may have already " +
                                        "purged it {}",
                                refStreamDefinition);
                    }

                    // Force final commit
                    batchingWriteTxn.commit();
                }
            } catch (Exception e) {
                try {
                    LOGGER.error(LogUtil.message("Error purging ref stream {}", refStreamDefinition, e));
                    // We are still under the ref stream def lock here
                    lmdbEnvironment.doWithWriteTxn(writeTxn ->
                            processingInfoDb.updateProcessingState(writeTxn,
                                    refStreamDefinitionBuf,
                                    ProcessingState.PURGE_FAILED,
                                    false));
                } catch (Exception e2) {
                    LOGGER.error("Error setting processing state to PURGE_FAILED for {}. {}",
                            refStreamDefinition,
                            e2.getMessage(),
                            e2);
                }
            }
        });
        return refStreamPurgeCountsRef.get() != null
                ? refStreamPurgeCountsRef.get()
                : RefStreamPurgeCounts.zero();
    }

    private RefStreamPurgeCounts purgeRefStreamDef(final RefStreamDefinition refStreamDefinition,
                                                   final RefDataProcessingInfo refDataProcessingInfo,
                                                   final ByteBuffer refStreamDefinitionBuf,
                                                   final BatchingWriteTxn batchingWriteTxn) {

        taskContext.info(() -> "Purging reference stream " +
                refStreamDefinition.getStreamId() + ":" +
                refStreamDefinition.getPartIndex());

        final String refStreamDefStr = LogUtil.message(
                "stream: {}, " +
                        "effective time: {}, " +
                        "pipeline: {}, " +
                        "pipeline version: {}, " +
                        "create time: {}, " +
                        "access time: {}, " +
                        "state: {}",
                refStreamDefinition.getStreamId(),
                refDataProcessingInfo.getEffectiveTime(),
                DocRefUtil.createSimpleDocRefString(refStreamDefinition.getPipelineDocRef()),
                refStreamDefinition.getPipelineVersion(),
                refDataProcessingInfo.getCreateTime(),
                refDataProcessingInfo.getLastAccessedTime(),
                refDataProcessingInfo.getProcessingState());

        LOGGER.info("Purging refStreamDefinition with {}", refStreamDefStr);

        RefStreamPurgeCounts refStreamSummaryInfo = RefStreamPurgeCounts.zero();
        try {
            // mark it is purge in progress, so if we are committing part way through
            // other processes will know it is a partial state.
            processingInfoDb.updateProcessingState(batchingWriteTxn.getTxn(),
                    refStreamDefinitionBuf,
                    ProcessingState.PURGE_IN_PROGRESS,
                    false);

            // purge the data associated with this ref stream def
            refStreamSummaryInfo = purgeRefStreamData(batchingWriteTxn, refStreamDefinition);

            //now delete the proc info entry
            LOGGER.debug("Deleting processing info entry for {}", refStreamDefinition);
            taskContext.info(() -> "Deleting processing info entry for " + refStreamDefinition);

            boolean didDeleteSucceed = processingInfoDb.delete(
                    batchingWriteTxn.getTxn(), refStreamDefinitionBuf);

            if (!didDeleteSucceed) {
                throw new RuntimeException("Processing info entry not found so was not deleted");
            }

            // Ensure we commit at the end of each ref stream
            batchingWriteTxn.commit();

            LOGGER.info("Completed purge of refStreamDefinition with stream {} (" +
                            "{} maps deleted, {} values deleted, {} values de-referenced)",
                    refStreamDefinition.getStreamId(),
                    refStreamSummaryInfo.mapsDeletedCount,
                    refStreamSummaryInfo.valuesDeletedCount,
                    refStreamSummaryInfo.valuesDeReferencedCount);

        } catch (Exception e) {
            refStreamSummaryInfo = refStreamSummaryInfo.increment(
                    0, 0, 0, false);

            LOGGER.error("Failed purge of refStreamDefinition with stream {} (" +
                            "{} maps deleted, {} values deleted, {} values de-referenced): {}",
                    refStreamDefinition.getStreamId(),
                    refStreamSummaryInfo.mapsDeletedCount,
                    refStreamSummaryInfo.valuesDeletedCount,
                    refStreamSummaryInfo.valuesDeReferencedCount,
                    e.getMessage(), e);

            try {
                // We are still under the ref stream def lock here
                processingInfoDb.updateProcessingState(
                        batchingWriteTxn.getTxn(),
                        refStreamDefinitionBuf,
                        ProcessingState.PURGE_FAILED,
                        false);
            } catch (Exception e2) {
                LOGGER.error("Unable to update processing state for ref stream {}: {}",
                        refStreamDefinition, e2.getMessage(), e2);
            }
            // Don't re-throw so we can move on to the next one
        }

        return refStreamSummaryInfo;
    }

    @NotNull
    private Optional<RefStreamDefinition> findNextRefStreamDef(
            final PooledByteBufferPair procInfoPooledBufferPair,
            final AtomicReference<ByteBuffer> currRefStreamDefBufRef,
            final Predicate<ByteBuffer> accessTimePredicate,
            final Txn<ByteBuffer> readTxn) {

        // ensure the buffers are cleared as we are using them in a loop
        procInfoPooledBufferPair.clear();

        final Optional<PooledByteBufferPair> optProcInfoBufferPair = processingInfoDb.getNextEntryAsBytes(
                readTxn,
                currRefStreamDefBufRef.get(),
                accessTimePredicate,
                procInfoPooledBufferPair);

        return optProcInfoBufferPair.map(procInfoBufferPair -> {
            RefStreamDefinition refStreamDefinition = processingInfoDb.deserializeKey(
                    procInfoBufferPair.getKeyBuffer());

            // update the current key buffer so we can search from here next time
            currRefStreamDefBufRef.set(procInfoBufferPair.getKeyBuffer());
            return refStreamDefinition;
        });
    }

    private RefStreamPurgeCounts purgeRefStreamData(final BatchingWriteTxn batchingWriteTxn,
                                                    final RefStreamDefinition refStreamDefinition) {

        LOGGER.debug("purgeRefStreamData({})", refStreamDefinition);

        RefStreamPurgeCounts summaryInfo = RefStreamPurgeCounts.zero();
        Optional<UID> optMapUid;
        try (PooledByteBuffer pooledUidBuffer = byteBufferPool.getPooledByteBuffer(UID.UID_ARRAY_LENGTH)) {
            do {
                // open a ranged cursor on the map forward table to scan all map defs for that stream def
                // for each map def get the map uid
                optMapUid = mapDefinitionUIDStore.getNextMapDefinition(
                        batchingWriteTxn.getTxn(), refStreamDefinition, pooledUidBuffer::getByteBuffer);

                if (optMapUid.isPresent()) {
                    final UID mapUid = optMapUid.get();
                    final MapDefinition mapDefinition = mapDefinitionUIDStore.get(
                                    batchingWriteTxn.getTxn(), mapUid)
                            .orElseThrow(() ->
                                    new RuntimeException(LogUtil.message(
                                            "We should be a mapDefinition if we have a UID, uid: {}",
                                            mapUid)));

                    LOGGER.debug("Found mapUid {} for refStreamDefinition {}", mapUid, refStreamDefinition);

                    final Tuple2<Integer, Integer> dataPurgeCounts = purgeMapData(
                            batchingWriteTxn, optMapUid.get());

                    summaryInfo = summaryInfo.increment(
                            1,
                            dataPurgeCounts._1(),
                            dataPurgeCounts._2());

                    LOGGER.info("  Purged map {}, {} values deleted, {} values de-referenced",
                            mapDefinition.getMapName(),
                            dataPurgeCounts._1(),
                            dataPurgeCounts._2());
                } else {
                    LOGGER.debug("No more map definitions to purge for refStreamDefinition {}", refStreamDefinition);
                }
            } while (optMapUid.isPresent());
        }

//        LAMBDA_LOGGER.info("Purged data for {} map(s) for {}", cnt, refStreamDefinition);
        return summaryInfo;
    }

    private Tuple2<Integer, Integer> purgeMapData(final BatchingWriteTxn batchingWriteTxn,
                                                  final UID mapUid) {

        LOGGER.debug("purgeMapData(writeTxn, {})", mapUid);

        LOGGER.debug("Deleting key/value entries and de-referencing/deleting their values");
        // loop over all keyValue entries for this mapUid and dereference/delete the associated
        // valueStore entry
        AtomicLong valueEntryDeleteCount = new AtomicLong();
        AtomicLong valueEntryDeReferenceCount = new AtomicLong();
        keyValueStoreDb.deleteMapEntries(
                batchingWriteTxn,
                mapUid,
                (writeTxn, keyValueStoreKeyBuffer, valueStoreKeyBuffer) -> {

                    //dereference this value, deleting it if required
                    deReferenceOrDeleteValue(
                            writeTxn,
                            valueStoreKeyBuffer,
                            valueEntryDeleteCount,
                            valueEntryDeReferenceCount);
                });
        LOGGER.debug(() -> LogUtil.message("Deleted {} value entries, de-referenced {} value entries",
                valueEntryDeleteCount.get(), valueEntryDeReferenceCount.get()));

        LOGGER.debug("Deleting range/value entries and de-referencing/deleting their values");
        // loop over all rangeValue entries for this mapUid and dereference/delete the associated
        // valueStore entry
        rangeStoreDb.deleteMapEntries(
                batchingWriteTxn,
                mapUid,
                (writeTxn, rangeValueStoreKeyBuffer, valueStoreKeyBuffer) -> {

                    //dereference this value, deleting it if required
                    deReferenceOrDeleteValue(
                            writeTxn,
                            valueStoreKeyBuffer,
                            valueEntryDeleteCount,
                            valueEntryDeReferenceCount);
                });
        LOGGER.debug("Deleting range/value entries and de-referencing/deleting their values");

        mapDefinitionUIDStore.deletePair(batchingWriteTxn.getTxn(), mapUid);

        return Tuple.of(valueEntryDeleteCount.intValue(), valueEntryDeReferenceCount.intValue());
    }

    private void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn,
                                          final ByteBuffer valueStoreKeyBuffer,
                                          final AtomicLong valueEntryDeleteCount,
                                          final AtomicLong valueEntryDeReferenceCount) {

        boolean wasDeleted = valueStore.deReferenceOrDeleteValue(writeTxn, valueStoreKeyBuffer);

        if (wasDeleted) {
            // we deleted the meta entry so now delete the value entry
            valueEntryDeleteCount.incrementAndGet();
        } else {
            // keep a count of the de-reference
            valueEntryDeReferenceCount.incrementAndGet();
        }
    }


    /**
     * Package-private for testing
     */
    void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition, final Runnable work) {
        doWithRefStreamDefinitionLock(refStreamDefStripedReentrantLock, refStreamDefinition, work);
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    @Override
    public void logAllContents() {
        logAllContents(LOGGER::debug);
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    @Override
    public void logAllContents(Consumer<String> logEntryConsumer) {
        databaseMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEach(lmdbDb -> lmdbDb.logDatabaseContents(logEntryConsumer));
    }

    void logContents(final String dbName) {
        doWithLmdb(dbName, LmdbDb::logDatabaseContents);
    }

    void doWithLmdb(final String dbName, final Consumer<LmdbDb> work) {
        LmdbDb lmdbDb = databaseMap.get(dbName);
        if (lmdbDb == null) {
            throw new IllegalArgumentException(LogUtil.message("No database with name {} exists", dbName));
        }
        work.accept(lmdbDb);
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    void logAllRawContents(Consumer<String> logEntryConsumer) {
        databaseMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEach(lmdbDb -> lmdbDb.logDatabaseContents(logEntryConsumer));
    }

    void logRawContents(final String dbName) {
        doWithLmdb(dbName, LmdbDb::logRawDatabaseContents);
    }

    long getEntryCount(final String dbName) {
        LmdbDb lmdbDb = databaseMap.get(dbName);
        if (lmdbDb == null) {
            throw new IllegalArgumentException(LogUtil.message("No database with name {} exists", dbName));
        }
        return lmdbDb.getEntryCount();
    }

    @Override
    public <T> T consumeEntryStream(final Function<Stream<RefStoreEntry>, T> streamFunction) {

        // TODO @AT This is all VERY crude. We should only be hitting the other DBs if we are returning
        //   a field from them or filtering on one of their fields. Also we should not be scanning over the
        //   whole of the kv/rv DBs, instead using start/stop keys built from the query expression

        // TODO @AT This is not ideal holding a txn open for the whole query (if the query takes a long time)
        //   as read txns prevent writers from writing to reclaimed space in the db, so the store can get quite big.
        //   see https://lmdb.readthedocs.io/en/release/#transaction-management

        return lmdbEnvironment.getWithReadTxn(readTxn -> {
            try (final CursorIterable<ByteBuffer> keyValueDbIterable = keyValueStoreDb.getLmdbDbi().iterate(
                    readTxn, KeyRange.all());
                    final CursorIterable<ByteBuffer> rangeValueDbIterable = rangeStoreDb.getLmdbDbi().iterate(
                            readTxn, KeyRange.all())) {

                // Transient caches of some of the low caridinality but high frequency lookups
                // Only provides limited performance gains.
                final Map<UID, MapDefinition> uidToMapDefMap = new HashMap<>();
                final Map<MapDefinition, RefDataProcessingInfo> mapDefToProcessingInfoMap = new HashMap<>();

                final Stream<RefStoreEntry> keyValueStream = buildKeyValueStoreEntryStream(
                        readTxn,
                        keyValueDbIterable,
                        uidToMapDefMap,
                        mapDefToProcessingInfoMap);

                final Stream<RefStoreEntry> rangeValueStream =
                        buildRangeValueStoreEntryStream(
                                readTxn,
                                rangeValueDbIterable,
                                uidToMapDefMap,
                                mapDefToProcessingInfoMap);

                final LongAdder entryCounter = new LongAdder();
                final Stream<RefStoreEntry> combinedStream = Stream.concat(keyValueStream, rangeValueStream)
                        .peek(entry -> entryCounter.increment());

                return LOGGER.logDurationIfDebugEnabled(
                        () ->
                                streamFunction.apply(combinedStream),
                        () ->
                                LogUtil.message("Scanned over {} entries", entryCounter.sum()));
            }
        });
    }

    @NotNull
    private Stream<RefStoreEntry> buildRangeValueStoreEntryStream(
            final Txn<ByteBuffer> readTxn,
            final CursorIterable<ByteBuffer> rangeValueDbIterable,
            final Map<UID, MapDefinition> uidToMapDefMap,
            final Map<MapDefinition, RefDataProcessingInfo> mapDefToProcessingInfoMap) {

        return StreamSupport.stream(rangeValueDbIterable.spliterator(), false)
                .map(rangeStoreDb::deserializeKeyVal)
                .map(entry -> {
                    final RangeStoreKey rangeStoreKey = entry.getKey();
                    final ValueStoreKey valueStoreKey = entry.getValue();
                    final String keyStr = rangeStoreKey.getKeyRange().getFrom() + "-"
                            + rangeStoreKey.getKeyRange().getTo();
                    return buildRefStoreEntry(
                            readTxn,
                            rangeStoreKey.getMapUid(),
                            keyStr,
                            valueStoreKey,
                            uidToMapDefMap,
                            mapDefToProcessingInfoMap);
                });
    }

    @NotNull
    private Stream<RefStoreEntry> buildKeyValueStoreEntryStream(
            final Txn<ByteBuffer> readTxn,
            final CursorIterable<ByteBuffer> keyValueDbIterable,
            final Map<UID, MapDefinition> uidToMapDefMap,
            final Map<MapDefinition, RefDataProcessingInfo> mapDefToProcessingInfoMap) {

        return StreamSupport.stream(keyValueDbIterable.spliterator(), false)
                .map(keyValueStoreDb::deserializeKeyVal)
                .map(entry -> {
                    final KeyValueStoreKey keyValueStoreKey = entry.getKey();
                    final ValueStoreKey valueStoreKey = entry.getValue();
                    final String keyStr = keyValueStoreKey.getKey();

                    return buildRefStoreEntry(readTxn,
                            keyValueStoreKey.getMapUid(),
                            keyStr,
                            valueStoreKey, uidToMapDefMap, mapDefToProcessingInfoMap);
                });
    }

    @Override
    public List<RefStoreEntry> list(final int limit) {
        return consumeEntryStream(stream -> stream
                .limit(limit)
                .collect(Collectors.toList()));
    }

    @Override
    public List<RefStoreEntry> list(final int limit,
                                    final Predicate<RefStoreEntry> filter) {

        return consumeEntryStream(stream -> stream
                .filter(filter != null
                        ? filter
                        : val -> true)
                .limit(limit)
                .collect(Collectors.toList()));
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit) {
        return listProcessingInfo(limit, refStreamProcessingInfo -> true);
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit,
                                                           final Predicate<ProcessingInfoResponse> filter) {

        final LongAdder entryCounter = new LongAdder();

        final List<ProcessingInfoResponse> items = new ArrayList<>();

        LOGGER.logDurationIfDebugEnabled(
                () -> {
                    items.addAll(lmdbEnvironment.getWithReadTxn(readTxn ->
                            processingInfoDb.streamEntries(
                                    readTxn,
                                    KeyRange.all(),
                                    procInfoEntryStream ->
                                            procInfoEntryStream
                                                    .peek(refStoreEntry ->
                                                            entryCounter.increment())
                                                    .map(entry -> {
                                                        return buildProcessingInfoResponse(readTxn, entry);
                                                    })
                                                    .filter(filter)
                                                    .limit(limit)
                                                    .collect(Collectors.toList())
                            )));
                },
                () ->
                        LogUtil.message("Scanned over {} entries, returning {}",
                                entryCounter.sum(),
                                items.size()));
        return items;
    }

    private ProcessingInfoResponse buildProcessingInfoResponse(
            final Txn<ByteBuffer> readTxn,
            final Entry<RefStreamDefinition, RefDataProcessingInfo> entry) {

        final RefStreamDefinition refStreamDefinition = entry.getKey();
        final RefDataProcessingInfo refDataProcessingInfo = entry.getValue();

        // Sub-query to get the map names for this refStreamDefinition
        final List<MapDefinition> mapDefinitions = mapDefinitionUIDStore.getMapDefinitions(readTxn,
                refStreamDefinition);

        final Map<String, EntryCounts> mapNameToEntryCountsMap = new HashMap<>();
        for (final MapDefinition mapDefinition : mapDefinitions) {
            final Optional<UID> optMapUid = mapDefinitionUIDStore.get(readTxn, mapDefinition);

            optMapUid.ifPresent(mapUid -> {
                final long keyValueCount = keyValueStoreDb.getEntryCount(mapUid, readTxn);
                final long rangeValueCount = rangeStoreDb.getEntryCount(mapUid, readTxn);
                mapNameToEntryCountsMap.put(
                        mapDefinition.getMapName(),
                        new EntryCounts(keyValueCount, rangeValueCount));
            });
        }

        return new ProcessingInfoResponse(
                refStreamDefinition,
                refDataProcessingInfo,
                mapNameToEntryCountsMap);
    }

    private RefStoreEntry buildRefStoreEntry(
            final Txn<ByteBuffer> readTxn,
            final UID mapUid,
            final String key,
            final ValueStoreKey valueStoreKey,
            final Map<UID, MapDefinition> uidToMapDefMap,
            final Map<MapDefinition, RefDataProcessingInfo> mapDefToProcessingInfoMap) {

        LOGGER.trace("mapUid: {}", mapUid);
        // Cache the map def lookups as we only have a handful and it saves the deser cost
        final MapDefinition mapDefinition = uidToMapDefMap.computeIfAbsent(
                mapUid,
                uid ->
                        mapDefinitionUIDStore.get(readTxn, mapUid)
                                .orElseThrow(() ->
                                        new RuntimeException("No MapDefinition for UID " + mapUid.toString())));

        LOGGER.trace("mapDefinition: {}", mapDefinition.toString());
        // Cache the ref stream lookups as we only have a handful and it saves the deser cost
        final RefDataProcessingInfo refDataProcessingInfo = mapDefToProcessingInfoMap.computeIfAbsent(
                mapDefinition,
                mapDefinition2 ->
                        processingInfoDb.get(readTxn,
                                        mapDefinition.getRefStreamDefinition())
                                .orElse(null));

        final String value = getReferenceDataValue(readTxn, key, valueStoreKey);

        // Not ideal having to re-serialise the key
        final int valueReferenceCount = valueStore.getReferenceCount(readTxn, valueStoreKey)
                .orElse(-1);

        return new RefStoreEntry(mapDefinition, key, value, valueReferenceCount, refDataProcessingInfo);
    }

    private String getReferenceDataValue(final Txn<ByteBuffer> readTxn,
                                         final String key,
                                         final ValueStoreKey valueStoreKey) {
        try {
            // As RefDataValue is just wrapping a buffer for the xml values, which is tied to the LMDB cursor
            // these two lines need to be done with nothing in between that would alter the cursor position
            final RefDataValue refDataValue = valueStore.get(readTxn, valueStoreKey)
                    .orElse(null);

            return refDataValueConverter.refDataValueToString(refDataValue);
        } catch (Exception e) {
            LOGGER.error("Error getting value for key " + key, e);

            // Return a value so the once bad value doesn't break the whole resultset
            return "[ERROR: " + e.getMessage() + "]";
        }
    }

//    private Instant getPurgeCutOffEpoch(final StroomDuration purgeAge) {
//        return Instant.now().minus(purgeAge.getDuration());
//    }

//    private Instant getPurgeCutOffEpoch(final Instant now, final StroomDuration purgeAgeMs) {
//        return now.minus(purgeAgeMs.getDuration());
//    }

    private PooledByteBuffer getAccessTimeCutOffBuffer(final Instant purgeCutOff) {

        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(Long.BYTES);
        pooledByteBuffer.getByteBuffer().putLong(purgeCutOff.toEpochMilli());
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final Tuple2<Optional<Instant>, Optional<Instant>> lastAccessedTimeRange =
                    processingInfoDb.getLastAccessedTimeRange();

            final ReferenceDataConfig referenceDataConfig = referenceDataConfigProvider.get();

            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this::getSystemInfo)
                    .addDetail("Path", lmdbEnvironment.getLocalDir().toAbsolutePath().normalize())
                    .addDetail("Environment max size", referenceDataConfig.getLmdbConfig().getMaxStoreSize())
                    .addDetail("Environment current size",
                            ModelStringUtil.formatIECByteSizeString(getSizeOnDisk()))
                    .addDetail("Purge age", referenceDataConfig.getPurgeAge())
                    .addDetail("Purge cut off",
                            TimeUtils.durationToThreshold(referenceDataConfig.getPurgeAge()).toString())
                    .addDetail("Max readers", referenceDataConfig.getLmdbConfig().getMaxReaders())
                    .addDetail("Available read permits", lmdbEnvironment.getAvailableReadPermitCount())
                    .addDetail("Read-ahead enabled", referenceDataConfig.getLmdbConfig().isReadAheadEnabled())
                    .addDetail("Current buffer pool size", byteBufferPool.getCurrentPoolSize())
                    .addDetail("Earliest lastAccessedTime", lastAccessedTimeRange._1()
                            .map(Instant::toString)
                            .orElse(null))
                    .addDetail("Latest lastAccessedTime", lastAccessedTimeRange._2()
                            .map(Instant::toString)
                            .orElse(null));

            lmdbEnvironment.doWithReadTxn(txn -> {
                builder.addDetail("Database entry counts", databaseMap.entrySet().stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(
                                Map.Entry::getKey,
                                entry ->
                                        entry.getValue().getEntryCount(txn))));
            });
            return builder.build();
        } catch (RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    public long getSizeOnDisk() {
        return lmdbEnvironment.getSizeOnDisk();
    }

    private static final class PurgeCounts {

        final int refStreamDefsDeletedCount;
        final int refStreamDefsFailedCount;
        final RefStreamPurgeCounts refStreamPurgeCounts;
        private static final PurgeCounts ZERO = new PurgeCounts(
                0,
                0,
                RefStreamPurgeCounts.zero());

        private PurgeCounts(final int refStreamDefsDeletedCount,
                            final int refStreamFailedCount,
                            final RefStreamPurgeCounts refStreamPurgeCounts) {
            this.refStreamDefsDeletedCount = refStreamDefsDeletedCount;
            this.refStreamDefsFailedCount = refStreamFailedCount;
            this.refStreamPurgeCounts = refStreamPurgeCounts;
        }

        public static PurgeCounts zero() {
            return ZERO;
        }

        public PurgeCounts increment(final RefStreamPurgeCounts refStreamPurgeCounts) {
            if (refStreamPurgeCounts.isZero()) {
                return new PurgeCounts(
                        refStreamDefsDeletedCount,
                        refStreamDefsFailedCount,
                        this.refStreamPurgeCounts);
            } else if (refStreamPurgeCounts.isSuccess) {
                return new PurgeCounts(
                        refStreamDefsDeletedCount + 1,
                        refStreamDefsFailedCount,
                        this.refStreamPurgeCounts.add(refStreamPurgeCounts));
            } else {
                return new PurgeCounts(
                        refStreamDefsDeletedCount,
                        refStreamDefsFailedCount + 1,
                        this.refStreamPurgeCounts.add(refStreamPurgeCounts));
            }
        }
    }

    private static final class RefStreamPurgeCounts {

        final int mapsDeletedCount;
        final int valuesDeletedCount;
        final int valuesDeReferencedCount;
        final boolean isSuccess;

        private static final RefStreamPurgeCounts ZERO = new RefStreamPurgeCounts(
                0,
                0,
                0,
                true);

        private RefStreamPurgeCounts(final int mapsDeletedCount,
                                     final int valuesDeletedCount,
                                     final int valuesDeReferencedCount,
                                     final boolean isSuccess) {
            this.mapsDeletedCount = mapsDeletedCount;
            this.valuesDeletedCount = valuesDeletedCount;
            this.valuesDeReferencedCount = valuesDeReferencedCount;
            this.isSuccess = isSuccess;
        }

        public static RefStreamPurgeCounts zero() {
            return ZERO;
        }

        public RefStreamPurgeCounts add(final RefStreamPurgeCounts other) {
            return increment(
                    other.mapsDeletedCount,
                    other.valuesDeletedCount,
                    other.valuesDeReferencedCount
            );
        }

        public RefStreamPurgeCounts increment(final int mapsDeletedDelta,
                                              final int valuesDeletedDelta,
                                              final int valuesDeReferencedDelta) {
            return increment(
                    mapsDeletedDelta,
                    valuesDeletedDelta,
                    valuesDeReferencedDelta,
                    isSuccess);
        }

        public RefStreamPurgeCounts increment(final int mapsDeletedDelta,
                                              final int valuesDeletedDelta,
                                              final int valuesDeReferencedDelta,
                                              final boolean isSuccess) {
            return new RefStreamPurgeCounts(
                    mapsDeletedCount + mapsDeletedDelta,
                    valuesDeletedCount + valuesDeletedDelta,
                    valuesDeReferencedCount + valuesDeReferencedDelta,
                    isSuccess && this.isSuccess);
        }

        public boolean isZero() {
            return mapsDeletedCount == 0
                    && valuesDeletedCount == 0
                    && valuesDeReferencedCount == 0;
        }
    }
}
