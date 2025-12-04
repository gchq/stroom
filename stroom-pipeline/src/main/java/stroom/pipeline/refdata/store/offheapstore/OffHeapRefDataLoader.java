/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.databases.EntryStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.pipeline.refdata.store.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RangeStoreKeySerde;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.NullSafeExtra;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Striped;
import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class for adding multiple items to the {@link RefDataOffHeapStore} within a single
 * write transaction. Accessed via
 * {@link stroom.pipeline.refdata.store.RefDataStore#doWithLoaderUnlessComplete(RefStreamDefinition, long, Consumer)}.
 * Commits data loaded so far every N entries, where N is maxPutsBeforeCommit.
 * If a value of maxPutsBeforeCommit is greater than one then processing should be kept
 * as lightweight as possible to avoid holding on to a write txn for too long.
 * The transaction will be committed when the loader is closed.
 * The loader instance is NOT thread safe so must be used by a single thread.
 */
public class OffHeapRefDataLoader implements RefDataLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OffHeapRefDataLoader.class);
    protected static final int PAD_LENGTH = 14;
    public static final String INFO_TEXT_STAGING_ENTRIES = "Staging entries";

    private final Lock refStreamDefReentrantLock;
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final ProcessingInfoDb processingInfoDb;
    private final RefDataLmdbEnv refStoreLmdbEnv;
    private final OffHeapStagingStoreFactory offHeapStagingStoreFactory;
    private final RefStreamDefinition refStreamDefinition;
    private final RefDataOffHeapStore refDataOffHeapStore;
    private final TaskContext taskContext;
    private final long effectiveTimeMs;

    private int newEntriesCount = 0;
    private int replacedEntriesCount = 0;
    private int unchangedEntriesCount = 0;
    private int removedEntriesCount = 0;
    private int ignoredCount = 0;
    private int ignoredNullsCount = 0;

    // Atomic as they will be accessed by the server tasks screen
    private final AtomicInteger putsToStagingStoreCounter = new AtomicInteger(0);
    private final AtomicInteger putsToRefStoreCounter = new AtomicInteger(0);
    private boolean overwriteExistingEntries = false;
    private DurationTimer overallTimer = null;
    private DurationTimer transferStagedEntriesTimer = null;
    private DurationTimer loadIntoStagingTimer = null;
    private LoaderState currentLoaderState = LoaderState.NEW;
    private LoaderMode loaderMode = null;

    private final PooledByteBuffer keyValuePooledKeyBuffer;
    private final PooledByteBuffer rangeValuePooledKeyBuffer;
    private final PooledByteBuffer valueStorePooledKeyBuffer;
    private final PooledByteBuffer pooledUidBuffer;
    private final List<PooledByteBuffer> pooledByteBuffers = new ArrayList<>();
    private int maxPutsBeforeCommit = 0;
    private KeyPutOutcomeHandler keyPutOutcomeHandler = null;
    private RangePutOutcomeHandler rangePutOutcomeHandler = null;
    private OffHeapStagingStore offHeapStagingStore = null;

    @Inject
    OffHeapRefDataLoader(@Assisted final Striped<Lock> refStreamDefStripedReentrantLock,
                         @Assisted final RefStreamDefinition refStreamDefinition,
                         @Assisted final long effectiveTimeMs,
                         @Assisted final RefDataOffHeapStore refDataOffHeapStore,
                         @Assisted final RefDataLmdbEnv refStoreLmdbEnv,
                         final KeyValueStoreDb keyValueStoreDb,
                         final RangeStoreDb rangeStoreDb,
                         final ValueStore valueStore,
                         final MapDefinitionUIDStore mapDefinitionUIDStore,
                         final ProcessingInfoDb processingInfoDb,
                         final OffHeapStagingStoreFactory offHeapStagingStoreFactory,
                         final TaskContextFactory taskContextFactory) {

        this.keyValueStoreDb = keyValueStoreDb;
        this.rangeStoreDb = rangeStoreDb;
        this.processingInfoDb = processingInfoDb;
        this.offHeapStagingStoreFactory = offHeapStagingStoreFactory;
        this.valueStore = valueStore;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
        this.refStoreLmdbEnv = refStoreLmdbEnv;
        this.refStreamDefinition = refStreamDefinition;
        this.refDataOffHeapStore = refDataOffHeapStore;
        this.effectiveTimeMs = effectiveTimeMs;

        // get three buffers to (re)use for the life of the loader
        this.keyValuePooledKeyBuffer = getAndRegisterPooledByteBuffer(keyValueStoreDb::getPooledKeyBuffer);
        this.rangeValuePooledKeyBuffer = getAndRegisterPooledByteBuffer(rangeStoreDb::getPooledKeyBuffer);
        this.valueStorePooledKeyBuffer = getAndRegisterPooledByteBuffer(valueStore::getPooledKeyBuffer);
        this.pooledUidBuffer = getAndRegisterPooledByteBuffer(mapDefinitionUIDStore::getUidPooledByteBuffer);
        this.taskContext = taskContextFactory.current();

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
        } catch (final InterruptedException e) {
            throw ProcessException.wrap(UncheckedInterruptedException.create(LogUtil.message(
                    "Acquisition of lock for {} aborted due to thread interruption",
                    refStreamDefinition), e));
        }
    }

    private PooledByteBuffer getAndRegisterPooledByteBuffer(final Supplier<PooledByteBuffer> pooledByteBufferSupplier) {
        final PooledByteBuffer pooledByteBuffer = pooledByteBufferSupplier.get();
        pooledByteBuffers.add(pooledByteBuffer);
        return pooledByteBuffer;
    }

    @Override
    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    @Override
    public PutOutcome initialise(final boolean overwriteExistingEntries) {
        return initialise(overwriteExistingEntries, LoaderMode.LOAD);
    }

    PutOutcome initialise(final boolean overwriteExistingEntries, final LoaderMode loaderMode) {
        if (this.loaderMode != null) {
            throw new RuntimeException(LogUtil.message("Loader already initialised"));
        }
        this.loaderMode = loaderMode;

        LOGGER.debug("initialise called, overwriteExistingEntries: {}", overwriteExistingEntries);
        checkCurrentState(LoaderState.NEW);

        overallTimer = DurationTimer.start();
        if (LoaderMode.LOAD.equals(loaderMode)) {
            loadIntoStagingTimer = DurationTimer.start();
        }

        this.overwriteExistingEntries = overwriteExistingEntries;

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                effectiveTimeMs,
                ProcessingState.LOAD_IN_PROGRESS);

        // Create this in the main store before we stage any data so any map definitions created
        // during staging can get purged later if the load is unsuccessful
        final PutOutcome putOutcome = processingInfoDb.put(
                refStreamDefinition, refDataProcessingInfo, true);

        if (isRegularLoad()) {
            // Create the LMDB staging store
            offHeapStagingStore = offHeapStagingStoreFactory.create(refStreamDefinition, mapDefinitionUIDStore);
        }

        currentLoaderState = LoaderState.INITIALISED;
        return putOutcome;
    }

    @Override
    public void markPutsComplete() {
        LOGGER.debug("markPutsComplete() called, currentLoaderState: {}, put count: {}",
                currentLoaderState, putsToRefStoreCounter);

        checkCurrentState(LoaderState.INITIALISED);
        offHeapStagingStore.completeLoad();
        loadIntoStagingTimer.stop();

        LOGGER.debug(() -> LogUtil.getDurationMessage(
                LogUtil.message("Load of {} entries into staging store for pipe {}",
                        putsToStagingStoreCounter, refStreamDefinition, getPipelineNameStr()),
                loadIntoStagingTimer.get(),
                putsToStagingStoreCounter.get()));

        // Pipe processing successful so transfer our staged data
        currentLoaderState = LoaderState.STAGED;
        // Update the meta data in the store
        updateProcessingState(ProcessingState.STAGED);
        // Move all the entries loaded into the staging store into the main ref store
        transferStagedEntries();
    }

    @Override
    public void completeProcessing(final ProcessingState processingState) {
        LOGGER.debug("completeProcessing() called, currentLoaderState: {}, processingState: {}, put count: {}",
                currentLoaderState, processingState, putsToRefStoreCounter);

        if (LoaderState.INITIALISED.equals(currentLoaderState)
            && ProcessingState.COMPLETE.equals(processingState)
            && isRegularLoad()) {

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

            switch (loaderMode) {
                case LOAD -> logLoadInfo(processingState);
                case MIGRATE -> logMigrationInfo(processingState);
                default -> throw new IllegalArgumentException(LogUtil.message("Unknown state {}", loaderMode));
            }

            currentLoaderState = LoaderState.COMPLETED;
        }
    }

    private void updateProcessingState(final ProcessingState processingState) {
        // We must clear the interrupt flag (if set) so that we can update the status in lmdb
        // which requires a lock and therefore would fail with an interrupted thread.
        final boolean wasInterrupted = Thread.interrupted();
        try {
            refStoreLmdbEnv.doWithWriteTxn(writeTxn -> {
                processingInfoDb.updateProcessingState(
                        writeTxn,
                        refStreamDefinition,
                        processingState,
                        true);
            });
        } finally {
            // Now we can reset the flag
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String getMapNamesStr() {
        return offHeapStagingStore.getStagedMapNames()
                .stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String getMapUidsStr() {
        return offHeapStagingStore.getStagedUids()
                .stream()
                .map(UID::getValue)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private String getPipelineNameStr() {
        return refStreamDefinition.getPipelineDocRef().getName() != null
                ? refStreamDefinition.getPipelineDocRef().getName()
                : refStreamDefinition.getPipelineDocRef().getUuid();
    }

    private void logLoadInfo(final ProcessingState processingState) {
        final Duration overalDuration = NullSafeExtra.durationTimer(overallTimer).get();
        final Duration loadIntoStagingDuration = NullSafeExtra.durationTimer(loadIntoStagingTimer).get();
        final Duration transferStagedEntriesDuration = NullSafeExtra.durationTimer(transferStagedEntriesTimer).get();

        LOGGER.info(LogUtil.inBoxOnNewLine(
                """
                        Processed {} reference entries with outcome {}
                        Store: {}
                        New entries:                {}
                        Null values ignored:        {}
                        dup-key value updated:      {}
                        dup-key value identical:    {}
                        dup-key entry removed:      {}
                        dup-key ignored:            {}
                        Map name(s):              {}
                        Map UID(s):               {}
                        Stream:                   {}
                        Pipeline name:            {}
                        Pipeline UUID:            {}
                        Pipeline version:         {}
                        Staging environment size: {}
                        Load to staging:          {}
                        Transfer from staging:    {}
                        Total:                    {}""",
                ModelStringUtil.formatCsv(putsToStagingStoreCounter),
                processingState,
                refStoreLmdbEnv.getName().orElse("?"),
                Strings.padStart(ModelStringUtil.formatCsv(newEntriesCount), PAD_LENGTH, ' '),
                Strings.padStart(ModelStringUtil.formatCsv(ignoredNullsCount), PAD_LENGTH, ' '),
                Strings.padStart(ModelStringUtil.formatCsv(replacedEntriesCount), PAD_LENGTH, ' '),
                Strings.padStart(ModelStringUtil.formatCsv(unchangedEntriesCount), PAD_LENGTH, ' '),
                Strings.padStart(ModelStringUtil.formatCsv(removedEntriesCount), PAD_LENGTH, ' '),
                Strings.padStart(ModelStringUtil.formatCsv(ignoredCount), PAD_LENGTH, ' '),
                getMapNamesStr(),
                getMapUidsStr(),
                refStreamDefinition.getStreamId(),
                Objects.requireNonNullElse(refStreamDefinition.getPipelineDocRef().getName(), "?"),
                refStreamDefinition.getPipelineDocRef().getUuid(),
                refStreamDefinition.getPipelineVersion(),
                ModelStringUtil.formatIECByteSizeString(offHeapStagingStore.getSizeOnDisk()),
                LogUtil.withPercentage(loadIntoStagingDuration, overalDuration),
                LogUtil.withPercentage(transferStagedEntriesDuration, overalDuration),
                overalDuration));
    }

    private void logMigrationInfo(final ProcessingState processingState) {
        final Duration overalDuration = NullSafeExtra.durationTimer(overallTimer).get();

        LOGGER.info(LogUtil.inBoxOnNewLine(
                """
                        Migrated {} reference entries into store '{}' with outcome {}
                        Store directory:  {}
                        Stream:           {}
                        Pipeline name:    {}
                        Pipeline UUID:    {}
                        Pipeline version: {}
                        Total time:       {}""",
                ModelStringUtil.formatCsv(newEntriesCount),
                refStoreLmdbEnv.getName(),
                processingState,
                NullSafe.get(refStoreLmdbEnv.getLocalDir(), Path::getFileName),
                refStreamDefinition.getStreamId(),
                Objects.requireNonNullElse(refStreamDefinition.getPipelineDocRef().getName(), "?"),
                refStreamDefinition.getPipelineDocRef().getUuid(),
                refStreamDefinition.getPipelineVersion(),
                overalDuration));
    }

    @Override
    public void setCommitInterval(final int maxPutsBeforeCommit) {
        Preconditions.checkArgument(maxPutsBeforeCommit >= 0);
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
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
        updateTaskContextInfoSupplier(INFO_TEXT_STAGING_ENTRIES);
        putsToStagingStoreCounter.incrementAndGet();
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
        updateTaskContextInfoSupplier(INFO_TEXT_STAGING_ENTRIES);
        putsToStagingStoreCounter.incrementAndGet();
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

        final int putsToStagingStoreCount = putsToStagingStoreCounter.get();
        LOGGER.debug("transferStagedEntries() - putsToStagingStoreCount: {}", putsToStagingStoreCount);

        if (putsToStagingStoreCount > 0) {
            transferStagedEntriesTimer = DurationTimer.start();
            try (final BatchingWriteTxn destBatchingWriteTxn = refStoreLmdbEnv.openBatchingWriteTxn(
                    maxPutsBeforeCommit)) {
                // We now hold the single write lock for the main ref store
                updateTaskContextInfoSupplier("Loading staged entries");
                transferStagedKeyValueEntries(destBatchingWriteTxn);
                transferStagedRangeValueEntries(destBatchingWriteTxn);

                // Final commit
                destBatchingWriteTxn.commit();
            }
            transferStagedEntriesTimer.stop();

            LOGGER.debug(() -> LogUtil.getDurationMessage(
                    LogUtil.message(
                            "Transfer of {} entries from staging store to ref data store for pipe",
                            ModelStringUtil.formatCsv(putsToStagingStoreCounter), getPipelineNameStr()),
                    transferStagedEntriesTimer.get(),
                    putsToStagingStoreCounter.get()));
        } else {
            updateTaskContextInfoSupplier("No staged entries");
        }
    }

    private <K> boolean isAppendableData(final BatchingWriteTxn batchingWriteTxn,
                                         final EntryStoreDb<K> entryStoreDb) {
        // Need to assess if we are appending data onto the end of the DB. This is so we can make
        // use of the MDB_APPEND put flag. If other loads are happening then it is possible one has loaded entries
        // since we generated these UIDs, so they are no longer at the head of the dbs.
        final Optional<UID> optMaxUidInDb = entryStoreDb.getMaxUid(batchingWriteTxn.getTxn(), pooledUidBuffer);
        final Set<UID> stagedUids = offHeapStagingStore.getStagedUids();

        final boolean isAppendable;
        if (stagedUids.isEmpty()) {
            LOGGER.debug("isAppendableData() - No staged UIDs");
            // Return value doesn't really matter as there is nothing to append/put
            isAppendable = true;
        } else if (optMaxUidInDb.isEmpty()) {
            // Totally empty DB, so we are appending
            LOGGER.debug("isAppendableData() - Empty optMaxUidInDb");
            isAppendable = true;
        } else {
            final UID maxUidInDb = optMaxUidInDb.get();
            final UID minUidInStaging = stagedUids.stream()
                    .sorted()
                    .findFirst()
                    .get();

            if (minUidInStaging.compareTo(maxUidInDb) > 0) {
                // The lowest staged UID is after all those in the DB, so we know we are appending. This relies on
                // new UIDs always increasing. If we ever change to reusing purged UIDs then this will not
                // work.
                isAppendable = true;
            } else {
                // Either overwriting a load or another load jumped in ahead of us, e.g. it started after us
                // but finished staging before us, so we are not appending. We could consider generating new UIDs
                // under lock, but the likelihood of a concurrent load is probably minimal so not worth it.
                LOGGER.warn("Unable to use APPEND mode, so ref load may be a bit slower. " +
                            "maxUidInDb: {}, minUidInStaging: {}, db: {}, refStreamDefinition: {}",
                        maxUidInDb.getValue(),
                        minUidInStaging.getValue(),
                        entryStoreDb.getClass().getSimpleName(),
                        refStreamDefinition);
                isAppendable = false;
            }
        }

        LOGGER.debug(() -> LogUtil.message("optMaxUidInDb: {}, stagedUids: '{}', isAppendable: {}",
                optMaxUidInDb.map(UID::getValue),
                stagedUids.stream()
                        .map(UID::getValue)
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")),
                isAppendable));

        return isAppendable;
    }

    private void transferStagedKeyValueEntries(final BatchingWriteTxn destBatchingWriteTxn) {
        final boolean isAppendableData = isAppendableData(destBatchingWriteTxn, keyValueStoreDb);
        // NOTE we are looping in key order, not in the order put to this loader
        offHeapStagingStore.forEachKeyValueEntry(entry -> {
            checkForTermination();
            final KeyValueStoreKey keyValueStoreKey = entry.getKey();
            final PutOutcome putOutcome = transferEntryToRefStore(
                    destBatchingWriteTxn,
                    keyValueStoreKey,
                    entry.getValue(),
                    keyValuePooledKeyBuffer,
                    keyValueStoreDb,
                    isAppendableData);

            NullSafe.consume(keyPutOutcomeHandler, handler -> handler.handleOutcome(
                    () -> offHeapStagingStore.getMapDefinition(keyValueStoreKey.getMapUid()),
                    keyValueStoreKey.getKey(),
                    putOutcome));
        });
    }

    private void transferStagedRangeValueEntries(final BatchingWriteTxn batchingWriteTxn) {
        final boolean isAppendableData = isAppendableData(batchingWriteTxn, rangeStoreDb);
        // NOTE we are looping in key order, not in the order put to this loader
        offHeapStagingStore.forEachRangeValueEntry(entry -> {
            checkForTermination();
            final RangeStoreKey rangeStoreKey = entry.getKey();
            final PutOutcome putOutcome = transferEntryToRefStore(
                    batchingWriteTxn,
                    rangeStoreKey,
                    entry.getValue(),
                    rangeValuePooledKeyBuffer,
                    rangeStoreDb,
                    isAppendableData);

            NullSafe.consume(rangePutOutcomeHandler, handler -> handler.handleOutcome(
                    () -> offHeapStagingStore.getMapDefinition(rangeStoreKey.getMapUid()),
                    rangeStoreKey.getKeyRange(),
                    putOutcome));
        });
    }

    private void checkForTermination() {
        if (taskContext.isTerminated() || Thread.currentThread().isInterrupted()) {
            LOGGER.debug(() ->
                    LogUtil.message("Task {} is terminated - isTerminated: {}, isInterrupted: {}",
                            taskContext.getTaskId(),
                            taskContext.isTerminated(),
                            Thread.currentThread().isInterrupted()));
            throw new TaskTerminatedException();
        }
    }

    <K> PutOutcome transferEntryToRefStore(final BatchingWriteTxn batchingWriteTxn,
                                           final K dbKey,
                                           final StagingValue stagingValue,
                                           final PooledByteBuffer pooledKeyBuffer,
                                           final EntryStoreDb<K> entryStoreDb,
                                           final boolean isAppendableData) {

        LOGGER.trace("transferEntryToRefStore({}, {}", dbKey, stagingValue);
        putsToRefStoreCounter.incrementAndGet();

        Objects.requireNonNull(dbKey);
        Objects.requireNonNull(stagingValue);
        Objects.requireNonNull(pooledKeyBuffer);

        final Txn<ByteBuffer> writeTxn = batchingWriteTxn.getTxn();
        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
        // ensure the buffer is clear as we are reusing the same one for each put
        keyBuffer.clear();
        // See if the store already has an entry for this lookup key
        entryStoreDb.serializeKey(keyBuffer, dbKey);
        // Even if isAppendableData is true, within the staged entries there may be dups that we have to
        // handle.
        final Optional<ByteBuffer> optCurrValueStoreKeyBuffer = entryStoreDb.getAsBytes(writeTxn, keyBuffer);

        // see if we have a value already for this key
        // if overwrite == false, we can just drop out here
        // if overwrite == true we need to de-reference the value (and maybe delete)
        // then create a new value, assuming they are different
        final PutOutcome putOutcome;
        if (optCurrValueStoreKeyBuffer.isPresent()) {
            if (!overwriteExistingEntries) {
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
                    final boolean areValuesEqual = valueStore.areValuesEqual(
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
                        putOutcome = putEntryWithValue(
                                writeTxn, entryStoreDb, stagingValue, keyBuffer, false);
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
                putOutcome = putEntryWithValue(writeTxn, entryStoreDb, stagingValue, keyBuffer, isAppendableData);
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
                                             final ByteBuffer keyBuffer,
                                             final boolean isAppending) {

        // First get/create the value entry, so we can then link our entry to it
        final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                writeTxn, valueStorePooledKeyBuffer, refDataValue);

        return entryStoreDb.put(
                writeTxn, keyBuffer, valueStoreKeyBuffer, overwriteExistingEntries, isAppending);
    }

    void migrateKeyEntry(final BatchingWriteTxn batchingWriteTxn,
                         final MapDefinition mapDefinition,
                         final ByteBuffer sourceKeyBuffer,
                         final byte valueTypeId,
                         final ByteBuffer refDataValueBuffer) {
        newEntriesCount++;
        // Need to assign a new map UID in the dest store
        final UID newMapUid = mapDefinitionUIDStore.getOrCreateUid(batchingWriteTxn.getTxn(),
                mapDefinition,
                pooledUidBuffer);

        final ByteBuffer destKeyBuffer = keyValuePooledKeyBuffer.getByteBuffer();
        destKeyBuffer.clear();
        KeyValueStoreKeySerde.copyWithNewUid(sourceKeyBuffer, destKeyBuffer, newMapUid);

        final StagingValue stagingValue = new MigrationValue(
                valueTypeId,
                refDataValueBuffer,
                valueStore.getValueStoreHashAlgorithm());

        // This is a migration, so we know the entries do not exist in the destination.
        // We are reading the entries from the source in key order and we are creating new UIDs
        // for each map, so we can run in append mode for speed.
        putEntryWithValue(
                batchingWriteTxn.getTxn(),
                keyValueStoreDb,
                stagingValue,
                destKeyBuffer,
                true);
    }

    void migrateRangeEntry(final BatchingWriteTxn batchingWriteTxn,
                           final MapDefinition mapDefinition,
                           final ByteBuffer sourceKeyBuffer,
                           final byte valueTypeId,
                           final ByteBuffer refDataValueBuffer) {
        newEntriesCount++;

        // Need to assign a new map UID in the dest store
        final UID newMapUid = mapDefinitionUIDStore.getOrCreateUid(batchingWriteTxn.getTxn(),
                mapDefinition,
                pooledUidBuffer);

        final ByteBuffer destKeyBuffer = rangeValuePooledKeyBuffer.getByteBuffer();
        destKeyBuffer.clear();
        RangeStoreKeySerde.copyWithNewUid(sourceKeyBuffer, destKeyBuffer, newMapUid);

        final StagingValue stagingValue = new MigrationValue(
                valueTypeId,
                refDataValueBuffer,
                valueStore.getValueStoreHashAlgorithm());

        // This is a migration, so we know the entries do not exist in the destination.
        // We are reading the entries from the source in key order and we are creating new UIDs
        // for each map, so we can run in append mode for speed.
        putEntryWithValue(
                batchingWriteTxn.getTxn(),
                rangeStoreDb,
                stagingValue,
                destKeyBuffer,
                true);
    }

    @Override
    public void close() {
        LOGGER.trace("Close called for {}", refStreamDefinition);

        if (!currentLoaderState.equals(LoaderState.COMPLETED)) {
            // This is likely if two threads try to load the same stream. One will win and load it
            // while the other will be blocked until it discovers it is already loaded then closes the loader
            LOGGER.debug("Reference data loader for {} was closed with a state of {}",
                    refStreamDefinition, currentLoaderState);
        }

        try {
            if (offHeapStagingStore != null) {
                offHeapStagingStore.close();
            }
        } catch (final Exception e) {
            LOGGER.error("Error closing offHeapStagingStore: {}", e.getMessage(), e);
        }

        // release our pooled buffers back to the pool
        pooledByteBuffers.forEach(pooledByteBuffer -> {
            try {
                pooledByteBuffer.close();
            } catch (final Exception e) {
                LOGGER.error("Error releasing pooled buffer: {}", e.getMessage(), e);
            }
        });

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
        for (final LoaderState loaderState : validStates) {
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

    private boolean isRegularLoad() {
        return LoaderMode.LOAD.equals(Objects.requireNonNull(loaderMode));
    }

    private boolean isMigration() {
        return LoaderMode.MIGRATE.equals(Objects.requireNonNull(loaderMode));
    }

    private void updateTaskContextInfoSupplier(final String extraText) {
        taskContext.info(() -> {
            final String extraTextArg = extraText != null
                    ? " - " + extraText
                    : "";
            return LogUtil.message(
                    "Loading reference data stream {}:{} into store {}{} - entries staged: {}, entries loaded: {}",
                    refStreamDefinition.getStreamId(),
                    refStreamDefinition.getPartNumber(),
                    refDataOffHeapStore.getName(),
                    extraTextArg,
                    putsToStagingStoreCounter.get(),
                    putsToRefStoreCounter.get());
        });
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


    public enum LoaderMode {
        /**
         * Loading reference entries via put calls
         */
        LOAD,
        /**
         * Migrating one store into another
         */
        MIGRATE
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        OffHeapRefDataLoader create(final Striped<Lock> refStreamDefStripedReentrantLock,
                                    final RefStreamDefinition refStreamDefinition,
                                    final long effectiveTimeMs,
                                    final RefDataOffHeapStore refDataOffHeapStore,
                                    final RefDataLmdbEnv refDataLmdbEnv);
    }
}
