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
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.databases.EntryStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.util.NullSafe;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import javax.inject.Inject;

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

    private final Lock refStreamDefReentrantLock;
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final ProcessingInfoDb processingInfoDb;
    private final RefDataLmdbEnv refStoreLmdbEnv;
    private final OffHeapStagingStoreFactory offHeapStagingStoreFactory;
    private final RefStreamDefinition refStreamDefinition;
    private final long effectiveTimeMs;

    private int newEntriesCount = 0;
    private int replacedEntriesCount = 0;
    private int unchangedEntriesCount = 0;
    private int removedEntriesCount = 0;
    private int ignoredCount = 0;
    private int ignoredNullsCount = 0;

    private int putsToStagingStoreCounter = 0;
    private int putsToRefStoreCounter = 0;
    private boolean overwriteExisting = false;
    private DurationTimer overallTimer = null;
    private DurationTimer transferStagedEntriesTimer = null;
    private DurationTimer loadIntoStagingTimer = null;
    private LoaderState currentLoaderState = LoaderState.NEW;

    private final PooledByteBuffer keyValuePooledKeyBuffer;
    private final PooledByteBuffer rangeValuePooledKeyBuffer;
    private final PooledByteBuffer valueStorePooledKeyBuffer;
    private final List<PooledByteBuffer> pooledByteBuffers = new ArrayList<>();
    private int maxPutsBeforeCommit = 0;
    private KeyPutOutcomeHandler keyPutOutcomeHandler = null;
    private RangePutOutcomeHandler rangePutOutcomeHandler = null;
    private OffHeapStagingStore offHeapStagingStore = null;

    @Inject
    OffHeapRefDataLoader(@Assisted final Striped<Lock> refStreamDefStripedReentrantLock,
                         @Assisted final RefStreamDefinition refStreamDefinition,
                         @Assisted final long effectiveTimeMs,
                         final KeyValueStoreDb keyValueStoreDb,
                         final RangeStoreDb rangeStoreDb,
                         final ValueStore valueStore,
                         final MapDefinitionUIDStore mapDefinitionUIDStore,
                         final ProcessingInfoDb processingInfoDb,
                         final RefDataLmdbEnv refStoreLmdbEnv,
                         final OffHeapStagingStoreFactory offHeapStagingStoreFactory) {

        this.keyValueStoreDb = keyValueStoreDb;
        this.rangeStoreDb = rangeStoreDb;
        this.processingInfoDb = processingInfoDb;
        this.offHeapStagingStoreFactory = offHeapStagingStoreFactory;
        this.valueStore = valueStore;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
        this.refStoreLmdbEnv = refStoreLmdbEnv;
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

        overallTimer = DurationTimer.start();
        loadIntoStagingTimer = DurationTimer.start();

        this.overwriteExisting = overwriteExisting;

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                effectiveTimeMs,
                ProcessingState.LOAD_IN_PROGRESS);

        // Create this in the main store before we stage any data so any map definitions created
        // during staging can get purged later if the load is unsuccessful
        final PutOutcome putOutcome = processingInfoDb.put(
                refStreamDefinition, refDataProcessingInfo, overwriteExisting);

        this.offHeapStagingStore = offHeapStagingStoreFactory.create(refStoreLmdbEnv, refStreamDefinition);

        currentLoaderState = LoaderState.INITIALISED;
        return putOutcome;
    }

    @Override
    public void markPutsComplete() {
        LOGGER.debug("markPutsComplete() called, currentLoaderState: {}, put count: {}",
                currentLoaderState, putsToRefStoreCounter);

        checkCurrentState(LoaderState.INITIALISED);
//            beginTxnIfRequired();
        offHeapStagingStore.completeLoad();
        loadIntoStagingTimer.stop();

        // Pipe processing successful so transfer our staged data
        currentLoaderState = LoaderState.STAGED;
        // Update the meta data in the store
        updateProcessingState(ProcessingState.STAGED);

        try {
            // Move all the entries loaded into the staging store into the main ref store
            transferStagedEntries();
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error transferring entries into the ref store: {}",
                    e.getMessage()), e);
        }
    }

    @Override
    public void completeProcessing(final ProcessingState processingState) {
        LOGGER.debug("completeProcessing() called, currentLoaderState: {}, processingState: {}, put count: {}",
                currentLoaderState, processingState, putsToRefStoreCounter);

        if (LoaderState.INITIALISED.equals(currentLoaderState)) {
            // markPutsComplete hasn't been called, so call it now
            markPutsComplete();
        }

        if (!VALID_COMPLETION_STATES.contains(processingState)) {
            throw new RuntimeException(LogUtil.message("Invalid processing state {}, should be one of {}",
                    processingState,
                    VALID_COMPLETION_STATES));
        }

        if (LoaderState.COMPLETED.equals(currentLoaderState)) {
            LOGGER.debug("Loader already completed, doing nothing");
        } else {
            checkCurrentState(LoaderState.INITIALISED, LoaderState.STAGED);
            // Set the processing info record to processingState and update the last update time
            updateProcessingState(processingState);
            logLoadInfo(processingState);
            currentLoaderState = LoaderState.COMPLETED;
        }
    }

    private void updateProcessingState(final ProcessingState processingState) {
        refStoreLmdbEnv.doWithWriteTxn(writeTxn -> {
            processingInfoDb.updateProcessingState(
                    writeTxn,
                    refStreamDefinition,
                    processingState,
                    true);
        });
    }

    private void logLoadInfo(final ProcessingState processingState) {

        final String mapNames = String.join(", ", offHeapStagingStore.getMapNames());
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
                        "pipeline: {} in {} (load to staging: {}, transfer from staging: {})",
                putsToStagingStoreCounter,
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
                overallTimer,
                loadIntoStagingTimer,
                transferStagedEntriesTimer);
    }

    @Override
    public void setCommitInterval(final int maxPutsBeforeCommit) {
        Preconditions.checkArgument(maxPutsBeforeCommit >= 0);
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;

//        if (maxPutsBeforeCommit == 0) {
//            commitIfRequireFunc = () -> {
//                // No mid-load commits required
//            };
//        } else {
//            commitIfRequireFunc = () -> {
//                if (putsToRefStoreCounter % maxPutsBeforeCommit == 0) {
//                    LOGGER.trace("Committing with putsCounter {}, maxPutsBeforeCommit {}",
//                            putsToRefStoreCounter, maxPutsBeforeCommit);
//                    commit();
//                }
//            };
//        }
    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final String key,
                    final StagingValue refDataValue) {
        LOGGER.trace("put({}, {}, {}", mapDefinition, key, refDataValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(key);
        Objects.requireNonNull(refDataValue);
        checkCurrentState(LoaderState.INITIALISED);

        // Stage the value
        putsToStagingStoreCounter++;
        offHeapStagingStore.put(mapDefinition, key, refDataValue);
    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final Range<Long> keyRange,
                    final StagingValue stagingValue) {
        LOGGER.trace("put({}, {}, {}", mapDefinition, keyRange, stagingValue);
        Objects.requireNonNull(mapDefinition);
        Objects.requireNonNull(keyRange);
        Objects.requireNonNull(stagingValue);
        checkCurrentState(LoaderState.INITIALISED);

        // Stage the value
        putsToStagingStoreCounter++;
        offHeapStagingStore.put(mapDefinition, keyRange, stagingValue);
    }

    @Override
    public void setKeyPutOutcomeHandler(final KeyPutOutcomeHandler keyPutOutcomeHandler) {
        this.keyPutOutcomeHandler = keyPutOutcomeHandler;
    }

    @Override
    public void setRangePutOutcomeHandler(final RangePutOutcomeHandler rangePutOutcomeHandler) {
        this.rangePutOutcomeHandler = rangePutOutcomeHandler;
    }

    private void transferStagedEntries() {
        checkCurrentState(LoaderState.STAGED);

        transferStagedEntriesTimer = DurationTimer.start();
        try (final BatchingWriteTxn batchingWriteTxn = refStoreLmdbEnv.openBatchingWriteTxn(maxPutsBeforeCommit)) {
            // NOTE we are looping in key order, not in the order put to this loader
            offHeapStagingStore.forEachKeyValueEntry(entry -> {
                final KeyValueStoreKey keyValueStoreKey = entry.getKey();
                final PutOutcome putOutcome = transferEntryToRefStore(
                        batchingWriteTxn,
                        keyValueStoreKey,
                        entry.getValue(),
                        keyValuePooledKeyBuffer,
                        keyValueStoreDb);

                NullSafe.consume(keyPutOutcomeHandler, handler -> handler.handleOutcome(
                        () -> offHeapStagingStore.getMapDefinition(keyValueStoreKey.getMapUid()),
                        keyValueStoreKey.getKey(),
                        putOutcome));
            });

            offHeapStagingStore.forEachRangeValueEntry(entry -> {
                final RangeStoreKey rangeStoreKey = entry.getKey();
                final PutOutcome putOutcome = transferEntryToRefStore(
                        batchingWriteTxn,
                        rangeStoreKey,
                        entry.getValue(),
                        rangeValuePooledKeyBuffer,
                        rangeStoreDb);

                NullSafe.consume(rangePutOutcomeHandler, handler -> handler.handleOutcome(
                        () -> offHeapStagingStore.getMapDefinition(rangeStoreKey.getMapUid()),
                        rangeStoreKey.getKeyRange(),
                        putOutcome));
            });
            // Final commit
            batchingWriteTxn.commit();
        }
        transferStagedEntriesTimer.stop();

        LOGGER.debug("Completed transfer of {} entries from staging store to ref data store in {}",
                putsToStagingStoreCounter, transferStagedEntriesTimer);
    }

    private <K> PutOutcome transferEntryToRefStore(final BatchingWriteTxn batchingWriteTxn,
                                                   final K dbKey,
                                                   final StagingValue stagingValue,
                                                   final PooledByteBuffer pooledKeyBuffer,
                                                   final EntryStoreDb<K> entryStoreDb) {

        LOGGER.trace("transferEntryToRefStore({}, {}", dbKey, stagingValue);
        putsToRefStoreCounter++;

        Objects.requireNonNull(dbKey);
        Objects.requireNonNull(stagingValue);
        Objects.requireNonNull(pooledKeyBuffer);

        final Txn<ByteBuffer> writeTxn = batchingWriteTxn.getTxn();
        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
        // ensure the buffer is clear as we are reusing the same one for each put
        keyBuffer.clear();
        // See if the store already has an entry for this lookup key
        entryStoreDb.serializeKey(keyBuffer, dbKey);
        final Optional<ByteBuffer> optCurrValueStoreKeyBuffer = entryStoreDb.getAsBytes(writeTxn, keyBuffer);

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

                if (stagingValue.isNullValue()) {
                    // New value is null, so we need to de-reference/delete the old one
                    valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

                    // Delete the existing entry
                    entryStoreDb.delete(writeTxn, keyBuffer);

                    putOutcome = PutOutcome.replacedEntry();
                    removedEntriesCount++;
                } else {
                    boolean areValuesEqual = valueStore.areValuesEqual(
                            writeTxn, currValueStoreKeyBuffer, stagingValue);
                    if (areValuesEqual) {
                        // value is the same as the existing value so nothing to do
                        // and no ref counts to change
                        // We haven't really replaced the entry but as they are the same, that is the effect
                        putOutcome = PutOutcome.replacedEntry();
                        unchangedEntriesCount++;
                    } else {
                        // value is different so we need to de-reference the old one
                        valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

                        // Now create the replacement entry (and a value if one does not already exist)
                        putOutcome = putEntryWithValue(writeTxn, entryStoreDb, stagingValue, keyBuffer);
//                        putOutcome = entryCreationFunc.apply(refDataValue, keyBuffer);
                        replacedEntriesCount++;
                    }
                }
            }
        } else {
            // no existing valueStoreKey so create the entry+value if non-null
            if (stagingValue.isNullValue()) {
                putOutcome = PutOutcome.success();
                ignoredNullsCount++;
            } else {
                putOutcome = putEntryWithValue(writeTxn, entryStoreDb, stagingValue, keyBuffer);
                newEntriesCount++;
            }
        }

        batchingWriteTxn.commitIfRequired();
        keyBuffer.clear();

        LOGGER.trace("Returning outcome: {}, inputCount: {}, newEntriesCount: {}, " +
                        "replacedEntriesCount: {}, removedEntriesCount: {}, unchangedEntriesCount: {}, " +
                        "ignoredCount: {}, nullCount: {}",
                putOutcome, putsToStagingStoreCounter, newEntriesCount, replacedEntriesCount, removedEntriesCount,
                unchangedEntriesCount, ignoredCount, ignoredNullsCount);

        return putOutcome;
    }

    private <K> PutOutcome putEntryWithValue(final Txn<ByteBuffer> writeTxn,
                                             final EntryStoreDb<K> entryStoreDb,
                                             final StagingValue refDataValue,
                                             final ByteBuffer keyBuffer) {

        // First get/create the value entry, so we can then link our entry to it
        final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                writeTxn, valueStorePooledKeyBuffer, refDataValue, overwriteExisting);

        return entryStoreDb.put(
                writeTxn, keyBuffer, valueStoreKeyBuffer, overwriteExisting, true);
    }

    @Override
    public void close() {
        LOGGER.trace("Close called for {}", refStreamDefinition);

        if (!currentLoaderState.equals(LoaderState.COMPLETED)) {
            LOGGER.warn("Reference data loader for {} was closed with a state of {}",
                    refStreamDefinition, currentLoaderState);
        }

        try {
            offHeapStagingStore.close();
        } catch (Exception e) {
            LOGGER.error("Error closing offHeapStagingStore: {}", e.getMessage(), e);
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


    // --------------------------------------------------------------------------------


    private enum LoaderState {
        /**
         * New loader with no change to the store.
         */
        NEW,
        /**
         * Loader initialised and processing info record written to store.
         */
        INITIALISED,
        /**
         * All data loaded into the staging store ready for transfer.
         */
        STAGED,
        /**
         * The load into the main store is complete.
         */
        COMPLETED,
        /**
         * The loader has been closed and all resources freed.
         */
        CLOSED
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        OffHeapRefDataLoader create(final Striped<Lock> refStreamDefStripedReentrantLock,
                                    final RefStreamDefinition refStreamDefinition,
                                    final long effectiveTimeMs);
    }
}
