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

import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
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

    private Txn<ByteBuffer> writeTxn = null;
    private Instant txnStartTime = null;
    private final RefDataOffHeapStore refDataOffHeapStore;
    private final Lock refStreamDefReentrantLock;

    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final ProcessingInfoDb processingInfoDb;

    private final Env<ByteBuffer> lmdbEnvironment;
    private final RefStreamDefinition refStreamDefinition;
    private final long effectiveTimeMs;
    private Runnable commitIfRequireFunc = () -> {}; // default position is to not commit mid-load

    private int inputCount = 0;
    private int newEntriesCount = 0;
    private int replacedEntriesCount = 0;
    private int unchangedEntriesCount = 0;
    private int ignoredCount = 0;

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
                         final Env<ByteBuffer> lmdbEnvironment,
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
        LAMBDA_LOGGER.logDurationIfDebugEnabled(
                () -> {
                    try {
                        LOGGER.debug("Acquiring lock for {}", refStreamDefinition);
                        // This will make any other threads trying to load the same refStreamDefinition
                        // block and wait for us
                        refStreamDefReentrantLock.lockInterruptibly();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(LogUtil.message(
                                "Acquisition of lock for {} aborted due to thread interruption",
                                refStreamDefinition));
                    }
                },
                () -> LogUtil.message("Acquiring lock for {}", refStreamDefinition));
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
                .collect(Collectors.joining(", "));

        final String pipeline = refStreamDefinition.getPipelineDocRef().getName() != null
                ? refStreamDefinition.getPipelineDocRef().getName()
                : refStreamDefinition.getPipelineDocRef().getUuid();

        LOGGER.info("Processed {} entries (new: {}, updated: {}, unchanged: {}, ignored: {}) " +
                        "with map name(s): [{}], stream: {}, pipeline: {} in {}",
                inputCount,
                newEntriesCount,
                replacedEntriesCount,
                unchangedEntriesCount,
                ignoredCount,
                mapNames,
                refStreamDefinition.getStreamId(),
                pipeline,
                loadDuration);

        currentLoaderState = LoaderState.COMPLETED;
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
        inputCount++;

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
        final Optional<ByteBuffer> optCurrValueStoreKeyBuffer = keyValueStoreDb.getAsBytes(
                writeTxn, keyValueKeyBuffer);

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

                boolean areValuesEqual = valueStore.areValuesEqual(
                        writeTxn, currValueStoreKeyBuffer, refDataValue);
                if (areValuesEqual) {
                    // value is the same as the existing value so nothing to do
                    // and no ref counts to change
                    // We haven't really replaced the entry but as they are the same, that is the effect
                    putOutcome = PutOutcome.replacedEntry();
                    unchangedEntriesCount++;
                } else {
                    // value is different so we need to de-reference the old one
                    // and getOrCreate the new one
                    valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

                    putOutcome = createKeyValue(refDataValue, keyValueKeyBuffer);
                    replacedEntriesCount++;
                }
            }
        } else {
            // no existing valueStoreKey so create the entries
            putOutcome = createKeyValue(refDataValue, keyValueKeyBuffer);
            newEntriesCount++;
        }

        commitIfRequired(putOutcome);
        keyValuePooledKeyBuffer.clear();

        return putOutcome;
    }


    @Override
    public PutOutcome put(final MapDefinition mapDefinition,
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

        final Optional<ByteBuffer> optCurrValueStoreKeyBuffer = rangeStoreDb.getAsBytes(
                writeTxn, rangeValueKeyBuffer);

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
//                    ValueStoreKey currentValueStoreKey = optCurrValueStoreKeyBuffer.get();

                boolean areValuesEqual = valueStore.areValuesEqual(
                        writeTxn, currValueStoreKeyBuffer, refDataValue);
                if (areValuesEqual) {
                    // value is the same as the existing value so nothing to do
                    // and no ref counts to change
                    // We haven't really replaced the entry but as they are the same, thay is the effect
                    putOutcome = PutOutcome.replacedEntry();
                    unchangedEntriesCount++;
                } else {
                    // value is different so we need to de-reference the old one
                    // and getOrCreate the new one
                    valueStore.deReferenceOrDeleteValue(writeTxn, currValueStoreKeyBuffer);

//                        final ByteBuffer valueStoreKeyBuffer = valueStoreDb.getOrCreate(
//                                writeTxn, refDataValue, valueStorePooledKeyBuffer, overwriteExisting);
                    //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
                    putOutcome = createRangeValue(refDataValue, rangeValueKeyBuffer);
                    replacedEntriesCount++;
                }
            }
        } else {
            // no existing valueStoreKey so create the entries
            putOutcome = createRangeValue(refDataValue, rangeValueKeyBuffer);
            newEntriesCount++;
        }

        commitIfRequired(putOutcome);
        rangeValuePooledKeyBuffer.clear();
        return putOutcome;
    }


    private PutOutcome createKeyValue(final RefDataValue refDataValue, final ByteBuffer keyValueKeyBuffer) {

        final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                writeTxn, valueStorePooledKeyBuffer, refDataValue, overwriteExisting);

        // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
        // do a get then optional put.
        return keyValueStoreDb.put(
                writeTxn, keyValueKeyBuffer, valueStoreKeyBuffer, overwriteExisting);
    }

    private PutOutcome createRangeValue(final RefDataValue refDataValue, final ByteBuffer rangeValueKeyBuffer) {

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
            LOGGER.warn("Reference data loader for {} was initialised but then closed before being completed",
                    refStreamDefinition);
        }
        if (writeTxn != null) {
            LOGGER.trace("Committing transaction (put count {})", putsCounter);
            writeTxn.commit();
            writeTxn.close();
        }
        // release our pooled buffers back to the pool
        pooledByteBuffers.forEach(PooledByteBuffer::release);

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
        writeTxn = lmdbEnvironment.txnWrite();
        if (LOGGER.isDebugEnabled()) {
            txnStartTime = Instant.now();
        }
    }

    private void commit() {
        if (writeTxn != null) {
            try {
                LOGGER.trace("Committing (put count {})", putsCounter);
                writeTxn.commit();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Txn held open for {}", (txnStartTime != null
                            ? Duration.between(txnStartTime, Instant.now())
                            : "-"));
                    txnStartTime = null;
                }
                writeTxn = null;
            } catch (Exception e) {
                throw new RuntimeException("Error committing write transaction", e);
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
                final UID newUid = mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDef, temporaryUidPooledBuffer);

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
