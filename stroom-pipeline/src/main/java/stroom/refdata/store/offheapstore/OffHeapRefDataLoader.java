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

package stroom.refdata.store.offheapstore;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Striped;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.store.MapDefinition;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataLoader;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.RefDataStore;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.refdata.util.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
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
                        LOGGER.debug("Acquiring lock for {}", refStreamDefinition);
                        refStreamDefReentrantLock.lockInterruptibly();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(LambdaLogger.buildMessage(
                                "Acquisition of lock for {} aborted due to thread interruption",
                                refStreamDefinition));
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
