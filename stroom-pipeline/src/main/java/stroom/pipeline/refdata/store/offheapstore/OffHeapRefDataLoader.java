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

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.EntryStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
public class OffHeapRefDataLoader implements RefDataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffHeapRefDataLoader.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OffHeapRefDataLoader.class);

    private WriteTxn writeTxn = null;
    private Instant txnStartTime = null;
    private final RefDataOffHeapStore refDataOffHeapStore;
    private final Lock refStreamDefReentrantLock;

    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final ProcessingInfoDb processingInfoDb;

    private final LmdbEnv lmdbEnvironment;
    private final RefStreamDefinition refStreamDefinition;
    private final long effectiveTimeMs;
    private Runnable commitIfRequireFunc = () -> {
    }; // default position is to not commit mid-load

    private int inputCount = 0;
    private int newEntriesCount = 0;
    private int replacedEntriesCount = 0;
    private int unchangedEntriesCount = 0;
    private int removedEntriesCount = 0;
    private int ignoredCount = 0;
    private int ignoredNullsCount = 0;

    private int putsCounter = 0;
    private boolean overwriteExisting = false;
    private Instant startTime = Instant.EPOCH;
    private LoaderState currentLoaderState = LoaderState.NEW;

    // TODO we could just hit lmdb each time, but there may be serde costs
    private final Map<MapDefinition, UID> mapDefinitionToUIDMap = new HashMap<>();

    private final PooledByteBuffer keyValuePooledKeyBuffer;
    private final PooledByteBuffer rangeValuePooledKeyBuffer;
    private final PooledByteBuffer valueStorePooledKeyBuffer;
    private final List<PooledByteBuffer> pooledByteBuffers = new ArrayList<>();

    private enum LoaderState {
        NEW,
        INITIALISED,
        COMPLETED,
        CLOSED
    }

    OffHeapRefDataLoader(final RefDataOffHeapStore refDataOffHeapStore,
                         final Striped<Lock> refStreamDefStripedReentrantLock,
                         final KeyValueStoreDb keyValueStoreDb,
                         final RangeStoreDb rangeStoreDb,
                         final ValueStore valueStore,
                         final MapDefinitionUIDStore mapDefinitionUIDStore,
                         final ProcessingInfoDb processingInfoDb,
                         final LmdbEnv lmdbEnvironment,
                         final RefStreamDefinition refStreamDefinition,
                         final long effectiveTimeMs) {

        this.refDataOffHeapStore = refDataOffHeapStore;
        this.keyValueStoreDb = keyValueStoreDb;
        this.rangeStoreDb = rangeStoreDb;
        this.processingInfoDb = processingInfoDb;

        this.valueStore = valueStore;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
        this.lmdbEnvironment = lmdbEnvironment;
        this.refStreamDefinition = refStreamDefinition;
        this.effectiveTimeMs = effectiveTimeMs;

        // get three buffers to (re)use for the life of the loader
        this.keyValuePooledKeyBuffer = keyValueStoreDb.getPooledKeyBuffer();
        this.rangeValuePooledKeyBuffer = rangeStoreDb.getPooledKeyBuffer();
        this.valueStorePooledKeyBuffer = valueStore.getPooledKeyBuffer();
        pooledByteBuffers.add(keyValuePooledKeyBuffer);
        pooledByteBuffers.add(rangeValuePooledKeyBuffer);
        pooledByteBuffers.add(valueStorePooledKeyBuffer);

        // Get the lock object for this refStreamDefinition
        this.refStreamDefReentrantLock = refStreamDefStripedReentrantLock.get(refStreamDefinition);

        final Instant time1 = Instant.now();
        try {
            LOGGER.debug("Acquiring lock for {}", refStreamDefinition);
            // As this is a striped lock we WILL block/wait on another thread with the same
            // refStreamDefinition but we MAY also block/wait on another thread with a different
            // refStreamDefinition depending on the number or stripes and the allocation of stripe
            // from refStreamDefinition.
            refStreamDefReentrantLock.lockInterruptibly();

            final Duration timeToAcquireLock = Duration.between(time1, Instant.now());
            LOGGER.debug("Acquired lock in {} for {}", timeToAcquireLock, refStreamDefinition);

            if (timeToAcquireLock.getSeconds() > 1 && !LOGGER.isDebugEnabled()) {
                LOGGER.info("Waited for {} to acquire lock for {}",
                        timeToAcquireLock, refStreamDefinition);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(LogUtil.message(
                    "Acquisition of lock for {} aborted due to thread interruption",
                    refStreamDefinition));
        }
    }

    @Override
    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    @Override
    public PutOutcome initialise(final boolean overwriteExisting) {

        LOGGER.debug("initialise called, overwriteExisting: {}", overwriteExisting);
        checkCurrentState(LoaderState.NEW);

        startTime = Instant.now();

        this.overwriteExisting = overwriteExisting;

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                effectiveTimeMs,
                ProcessingState.LOAD_IN_PROGRESS);

        final PutOutcome putOutcome = processingInfoDb.put(
                refStreamDefinition, refDataProcessingInfo, overwriteExisting);

        currentLoaderState = LoaderState.INITIALISED;
        return putOutcome;
    }

    @Override
    public void completeProcessing(final ProcessingState processingState) {
        LOGGER.trace("Completing processing with state {} (put count {})", processingState, putsCounter);

        if (!VALID_COMPLETION_STATES.contains(processingState)) {
            throw new RuntimeException(LogUtil.message("Invalid processing state {}, should be one of {}",
                    processingState,
                    VALID_COMPLETION_STATES));
        }

        if (LoaderState.COMPLETED.equals(currentLoaderState)) {
            LOGGER.debug("Loader already completed, doing nothing");
        } else {
            checkCurrentState(LoaderState.INITIALISED);
            beginTxnIfRequired();

            // Set the processing info record to processingState and update the last update time
            processingInfoDb.updateProcessingState(
                    writeTxn.getTxn(),
                    refStreamDefinition,
                    processingState,
                    true);

            // Need to commit the state change
            commit();

            logLoadInfo(processingState);

            currentLoaderState = LoaderState.COMPLETED;
        }
    }

    private void logLoadInfo(final ProcessingState processingState) {

        final Duration loadDuration = Duration.between(startTime, Instant.now());

        final String mapNames = mapDefinitionToUIDMap.keySet()
                .stream()
                .map(MapDefinition::getMapName)
                .collect(Collectors.joining(", "));

        final String pipeline = refStreamDefinition.getPipelineDocRef().getName() != null
                ? refStreamDefinition.getPipelineDocRef().getName()
                : refStreamDefinition.getPipelineDocRef().getUuid();

        LOGGER.info("Processed {} entries with outcome {} (" +
                        "new: {}, " +
                        "null values ignored: {}, " +
                        "dup-key value updated: {}, " +
                        "dup-key value identical: {}, " +
                        "dup-key entry removed: {}, " +
                        "dup-key ignored: {}) " +
                        "with map name(s): [{}], " +
                        "stream: {}, " +
                        "pipeline: {} in {}",
                inputCount,
                processingState,
                newEntriesCount,
                ignoredNullsCount,
                replacedEntriesCount,
                unchangedEntriesCount,
                removedEntriesCount,
                ignoredCount,
                mapNames,
                refStreamDefinition.getStreamId(),
                pipeline,
                loadDuration);
    }

    @Override
    public void setCommitInterval(final int maxPutsBeforeCommit) {
        Preconditions.checkArgument(maxPutsBeforeCommit >= 0);
        if (maxPutsBeforeCommit == 0) {
            commitIfRequireFunc = () -> {
                // No mid-load commits required
            };
        } else {
            commitIfRequireFunc = () -> {
                if (putsCounter % maxPutsBeforeCommit == 0) {
                    LOGGER.trace("Committing with putsCounter {}, maxPutsBeforeCommit {}",
                            putsCounter, maxPutsBeforeCommit);
                    commit();
                }
            };
        }
    }

    @Override
    public PutOutcome put(final MapDefinition mapDefinition,
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

        return doPut(
                keyValueStoreKey,
                refDataValue,
                keyValuePooledKeyBuffer,
                keyValueStoreDb);
    }

    @Override
    public PutOutcome put(final MapDefinition mapDefinition,
                          final Range<Long> keyRange,
                          final RefDataValue refDataValue) {
        LOGGER.trace("put({}, {}, {}", mapDefinition, keyRange, refDataValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(keyRange);
        Objects.requireNonNull(refDataValue);

        final UID mapUid = getOrCreateUid(mapDefinition);
        LOGGER.trace("Using mapUid {} for {}", mapUid, mapDefinition);
        final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, keyRange);

        return doPut(
                rangeStoreKey,
                refDataValue,
                rangeValuePooledKeyBuffer,
                rangeStoreDb);
    }

    private <K> PutOutcome doPut(final K dbKey,
                                 final RefDataValue refDataValue,
                                 final PooledByteBuffer pooledKeyBuffer,
                                 final EntryStoreDb<K> entryStoreDb) {

        LOGGER.trace("doPut({}, {}", dbKey, refDataValue);
        inputCount++;

        Objects.requireNonNull(dbKey);
        Objects.requireNonNull(refDataValue);
        Objects.requireNonNull(pooledKeyBuffer);

        checkCurrentState(LoaderState.INITIALISED);
        beginTxnIfRequired();

        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
        // ensure the buffer is clear as we are reusing the same one for each put
        keyBuffer.clear();
        // See if the store already has an entry for this lookup key
        entryStoreDb.serializeKey(keyBuffer, dbKey);
        final Optional<ByteBuffer> optCurrValueStoreKeyBuffer = entryStoreDb.getAsBytes(writeTxn.getTxn(), keyBuffer);

        // see if we have a value already for this key
        // if overwrite == false, we can just drop out here
        // if overwrite == true we need to de-reference the value (and maybe delete)
        // then create a new value, assuming they are different
        final PutOutcome putOutcome;
        if (optCurrValueStoreKeyBuffer.isPresent()) {
            if (!overwriteExisting) {
                // already have an entry for this key so drop out here
                // with nothing to do as we can't overwrite anything
                putOutcome = PutOutcome.failed();
                ignoredCount++;
            } else {
                // overwriting and we already have a value so see if the old and
                // new values are the same.
                final ByteBuffer currValueStoreKeyBuffer = optCurrValueStoreKeyBuffer.get();

                if (refDataValue instanceof NullValue) {
                    // New value is null, so we need to de-reference/delete the old one
                    valueStore.deReferenceOrDeleteValue(writeTxn.getTxn(), currValueStoreKeyBuffer);

                    // Delete the existing entry
                    entryStoreDb.delete(writeTxn.getTxn(), keyBuffer);

                    putOutcome = PutOutcome.replacedEntry();
                    removedEntriesCount++;
                } else {
                    boolean areValuesEqual = valueStore.areValuesEqual(
                            writeTxn.getTxn(), currValueStoreKeyBuffer, refDataValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        // We haven't really replaced the entry but as they are the same, that is the effect
                        putOutcome = PutOutcome.replacedEntry();
                        unchangedEntriesCount++;
                    } else {
                        // value is different so we need to de-reference the old one
                        valueStore.deReferenceOrDeleteValue(writeTxn.getTxn(), currValueStoreKeyBuffer);

                        // Now create the replacement entry (and a value if one does not already exist)
                        putOutcome = putEntryWithValue(entryStoreDb, refDataValue, keyBuffer);
//                        putOutcome = entryCreationFunc.apply(refDataValue, keyBuffer);
                        replacedEntriesCount++;
                    }
                }
            }
        } else {
            // no existing valueStoreKey so create the entry+value if non-null
            if (refDataValue instanceof NullValue) {
                putOutcome = PutOutcome.success();
                ignoredNullsCount++;
            } else {
                putOutcome = putEntryWithValue(entryStoreDb, refDataValue, keyBuffer);
                newEntriesCount++;
            }
        }

        commitIfRequired(putOutcome);
        pooledKeyBuffer.clear();

        LOGGER.trace("Returning outcome: {}, inputCount: {}, newEntriesCount: {}, " +
                        "replacedEntriesCount: {}, removedEntriesCount: {}, unchangedEntriesCount: {}, " +
                        "ignoredCount: {}, nullCount: {}",
                putOutcome, inputCount, newEntriesCount, replacedEntriesCount, removedEntriesCount,
                unchangedEntriesCount, ignoredCount, ignoredNullsCount);

        return putOutcome;
    }

    private <K> PutOutcome putEntryWithValue(final EntryStoreDb<K> entryStoreDb,
                                             final RefDataValue refDataValue,
                                             final ByteBuffer keyBuffer) {

        // First get/create the value so we can then link our entry to it
        final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                writeTxn.getTxn(), valueStorePooledKeyBuffer, refDataValue, overwriteExisting);

        // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
        // do a get then optional put.
        return entryStoreDb.put(
                writeTxn.getTxn(), keyBuffer, valueStoreKeyBuffer, overwriteExisting);
    }

    @Override
    public void close() {
        LOGGER.trace("Close called for {}", refStreamDefinition);

        if (currentLoaderState.equals(LoaderState.INITIALISED)) {
            LOGGER.warn("Reference data loader for {} was initialised but then closed before being completed",
                    refStreamDefinition);
        }

        try {
            commit();
        } catch (Exception e) {
            LOGGER.error("Error committing txn: {}", e.getMessage(), e);
        }

        try {
            // release our pooled buffers back to the pool
            pooledByteBuffers.forEach(PooledByteBuffer::release);
        } catch (Exception e) {
            LOGGER.error("Error releasing buffers: {}", e.getMessage(), e);
        }

        currentLoaderState = LoaderState.CLOSED;

        if (refStreamDefReentrantLock != null) {
            LOGGER.debug("Releasing lock for {}", refStreamDefinition);
            refStreamDefReentrantLock.unlock();
        }

        // uncomment this for development testing, handy for seeing what is in the stores post load
//            refDataOffHeapStore.logAllContents();
    }

    private void beginTxn() {
        if (writeTxn != null) {
            throw new RuntimeException("Transaction is already open");
        }
        LOGGER.trace("Beginning write transaction");
        writeTxn = lmdbEnvironment.openWriteTxn();
        if (LOGGER.isDebugEnabled()) {
            txnStartTime = Instant.now();
        }
    }

    private void commit() {
        if (writeTxn != null) {
            try {
                LOGGER.trace("Committing and closing txn (put count {})", putsCounter);
                writeTxn.commit();
                writeTxn.close();
                writeTxn = null;

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Txn held open for {}", (txnStartTime != null
                            ? Duration.between(txnStartTime, Instant.now())
                            : "-"));
                    txnStartTime = null;
                }
            } catch (Exception e) {
                throw new RuntimeException("Error committing and closing write transaction", e);
            }
        }
    }

    private UID getOrCreateUid(final MapDefinition mapDefinition) {

        // get the UID for this mapDefinition, and as we should only have a handful of mapDefinitions
        // per loader it makes sense to cache the MapDefinition=>UID mappings on heap for quicker access.
        final UID uid = mapDefinitionToUIDMap.computeIfAbsent(mapDefinition, mapDef -> {
            LOGGER.trace("MapDefinition not found in local cache so getting it from the store, {}", mapDefinition);
            beginTxnIfRequired();

            // The temporaryUidPooledBuffer may not be used if we find the map def in the DB
            try (final PooledByteBuffer temporaryUidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer()) {

                final PooledByteBuffer cachedUidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer();
                // Add it to the list so it will be released on close
                pooledByteBuffers.add(cachedUidPooledBuffer);

                // The returned UID wraps a direct buffer that is either owned by LMDB or came from
                // temporaryUidPooledBuffer so as we don't know when the txn will be closed or what
                // cursor operations will happen after this and because we want to cache it we will
                // make a copy of it using a buffer from the pool. The cache only lasts for the life
                // of the load and will only have a couple of UIDs in it so should be fine to hold on to them.
                final UID newUid = mapDefinitionUIDStore.getOrCreateUid(
                        writeTxn.getTxn(),
                        mapDef,
                        temporaryUidPooledBuffer);

                // Now clone it into a different buffer and wrap in a new UID instance
                final UID newUidClone = newUid.cloneToBuffer(cachedUidPooledBuffer.getByteBuffer());

                return newUidClone;
            }
        });
        return uid;
    }

    /**
     * To be called after each put
     */
    private void commitIfRequired(final PutOutcome putOutcome) {
        if (putOutcome.isSuccess()) {
            putsCounter++;
        }
        commitIfRequireFunc.run();
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
            throw new IllegalStateException(LogUtil.message("Current loader state: {}, valid states: {}",
                    currentLoaderState, Arrays.toString(validStates)));
        }
    }
}
