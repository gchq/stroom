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
import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.offheapstore.databases.RangeStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RefDataOffHeapStore implements RefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    private final Path dbDir;
    private final long maxSize;

    private final Env<ByteBuffer> lmdbEnvironment;

    // the DBs that make up the store
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ValueStoreDb valueStoreDb;
    private final MapUidForwardDb mapUidForwardDb;
    private final MapUidReverseDb mapUidReverseDb;
    private final ProcessingInfoDb processingInfoDb;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;

    /**
     * @param dbDir   The directory the LMDB environment will be created in, it must already exist
     * @param maxSize The max size in bytes of the environment. This should be less than the available
     *                disk space for dbDir. This size covers all DBs created in this environment.
     */
    @Inject
    RefDataOffHeapStore(
            @Assisted final Path dbDir,
            @Assisted final long maxSize,
            final KeyValueStoreDb.Factory keyValueStoreDbFactory,
            final ValueStoreDb.Factory valueStoreDbFactory,
            final RangeStoreDb.Factory rangeStoreDbFactory,
            final MapUidForwardDb.Factory mapUidForwardDbFactory,
            final MapUidReverseDb.Factory mapUidReverseDbFactory,
            final ProcessingInfoDb.Factory processingInfoDbFactory) {
        this.dbDir = dbDir;
        this.maxSize = maxSize;

        LOGGER.debug("Creating LMDB environment with maxSize: {}, dbDir {}", maxSize, dbDir.toAbsolutePath().toString());
        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.
        lmdbEnvironment = Env.<ByteBuffer>create()
                .setMapSize(maxSize)
                .setMaxDbs(1)
                .open(dbDir.toFile());

        // create all the databases
        this.keyValueStoreDb = keyValueStoreDbFactory.create(lmdbEnvironment);
        this.rangeStoreDb = rangeStoreDbFactory.create(lmdbEnvironment);
        this.valueStoreDb = valueStoreDbFactory.create(lmdbEnvironment);
        this.mapUidForwardDb = mapUidForwardDbFactory.create(lmdbEnvironment);
        this.mapUidReverseDb = mapUidReverseDbFactory.create(lmdbEnvironment);
        this.processingInfoDb = processingInfoDbFactory.create(lmdbEnvironment);
        this.mapDefinitionUIDStore = new MapDefinitionUIDStore(lmdbEnvironment, mapUidForwardDb, mapUidReverseDb);
    }

    /**
     * Returns the {@link RefDataProcessingInfo} for the passed {@link MapDefinition}, or an empty
     * {@link Optional} if there isn't one.
     */
    @Override
    public Optional<RefDataProcessingInfo> getProcessingInfo(final RefStreamDefinition refStreamDefinition) {
        return Optional.empty();
    }

    @Override
    public boolean isDataLoaded(final RefStreamDefinition refStreamDefinition) {

        // TODO we could optimise this so that it doesn't deser the whole object, instead just
        // extract the state value while in a txn
        return getProcessingInfo(refStreamDefinition)
                .map(RefDataProcessingInfo::getProcessingState)
                .filter(Predicate.isEqual(RefDataProcessingInfo.ProcessingState.COMPLETE))
                .isPresent();
    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final String key,
                    final Supplier<RefDataValue> refDataValueSupplier,
                    final boolean overwriteExistingValue) {

        boolean keyExists = false;

        if (!overwriteExistingValue && keyExists) {
            throw new RuntimeException("key exists");
        }

    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final Range<Long> keyRange,
                    final Supplier<RefDataValue> refDataValueSupplier,
                    final boolean overwriteExistingValue) {

        boolean keyExists = false;

        if (!overwriteExistingValue && keyExists) {
            throw new RuntimeException("key exists");
        }

    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                           final String key) {
        return Optional.empty();
    }

    @Override
    public Optional<RefDataValue> getValue(final ValueStoreKey valueStoreKey) {
        return Optional.empty();
    }

    @Override
    public Optional<RefDataValueProxy> getValueProxy(final MapDefinition mapDefinition, final String key) {
        return Optional.empty();
    }

    @Override
    public void consumeValue(final MapDefinition mapDefinition,
                             final String key,
                             final Consumer<RefDataValue> valueConsumer) {


    }

    @Override
    public void consumeValue(final ValueStoreKey valueStoreKey, final Consumer<RefDataValue> valueConsumer) {

    }

    @Override
    public void consumeBytes(final ValueStoreKey valueStoreKey, final Consumer<ByteBuffer> valueConsumer) {

    }

    @Override
    public <T> Optional<T> map(final MapDefinition mapDefinition,
                               final String key,
                               final Function<RefDataValue, T> valueMapper) {

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> map(final ValueStoreKey valueStoreKey, final Function<RefDataValue, T> valueMapper) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> mapBytes(final ValueStoreKey valueStoreKey, final Function<ByteBuffer, T> valueMapper) {
        return Optional.empty();
    }


    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     */
    public RefDataLoader loader(final RefStreamDefinition refStreamDefinition, final long effectiveTimeMs) {
        return new RefDataLoaderImpl(
                this,
                keyValueStoreDb,
                rangeStoreDb,
                valueStoreDb,
                mapUidForwardDb,
                mapUidReverseDb,
                mapDefinitionUIDStore, processingInfoDb,
                lmdbEnvironment,
                refStreamDefinition,
                effectiveTimeMs);
    }

    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env, final String name) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        return env.openDbi(name, DbiFlags.MDB_CREATE);
    }

    private UID createMapUID(final MapDefinition mapDefinition) {

        //Build a new UID based on +1 from the highest current UID
        //create forward mapping
        //create reverse mapping

        return null;
    }

    private boolean setProcessingInfo(final Txn<ByteBuffer> writeTxn,
                                      final RefStreamDefinition refStreamDefinition,
                                      final RefDataProcessingInfo refDataProcessingInfo,
                                      final boolean overwriteExisting) {

        return processingInfoDb.put(writeTxn, refStreamDefinition, refDataProcessingInfo, overwriteExisting);
    }

    private void updateProcessingState(final Txn<ByteBuffer> writeTxn,
                                       final RefStreamDefinition refStreamDefinition,
                                       final RefDataProcessingInfo.ProcessingState newProcessingState) {

        processingInfoDb.updateProcessingState(writeTxn, refStreamDefinition, newProcessingState);
    }


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
        private final KeyValueStoreDb keyValueStoreDb;
        private final RangeStoreDb rangeStoreDb;
        private final ValueStoreDb valueStoreDb;
        private final MapUidForwardDb mapUidForwardDb;
        private final MapUidReverseDb mapUidReverseDb;
        private final MapDefinitionUIDStore mapDefinitionUIDStore;
        private final ProcessingInfoDb processingInfoDb;
        private final Env<ByteBuffer> lmdbEnvironment;
        private boolean initialised = false;
        private final RefStreamDefinition refStreamDefinition;
        private final long effectiveTimeMs;
        private int maxPutsBeforeCommit = Integer.MAX_VALUE;
        private int putsCounter = 0;

        // TODO we could just hit lmdb each time, but there may be serde costs
        private final Map<MapDefinition, UID> mapDefinitionToUIDMap = new HashMap<>();

        private RefDataLoaderImpl(final RefDataOffHeapStore refDataOffHeapStore,
                                  final KeyValueStoreDb keyValueStoreDb,
                                  final RangeStoreDb rangeStoreDb,
                                  final ValueStoreDb valueStoreDb,
                                  final MapUidForwardDb mapUidForwardDb,
                                  final MapUidReverseDb mapUidReverseDb,
                                  final MapDefinitionUIDStore mapDefinitionUIDStore,
                                  final ProcessingInfoDb processingInfoDb,
                                  final Env<ByteBuffer> lmdbEnvironment,
                                  final RefStreamDefinition refStreamDefinition,
                                  final long effectiveTimeMs) {

            this.refDataOffHeapStore = refDataOffHeapStore;
            this.keyValueStoreDb = keyValueStoreDb;
            this.rangeStoreDb = rangeStoreDb;
            this.valueStoreDb = valueStoreDb;
            this.mapUidForwardDb = mapUidForwardDb;
            this.mapUidReverseDb = mapUidReverseDb;
            this.mapDefinitionUIDStore = mapDefinitionUIDStore;
            this.processingInfoDb = processingInfoDb;
            this.lmdbEnvironment = lmdbEnvironment;
            this.refStreamDefinition = refStreamDefinition;
            this.effectiveTimeMs = effectiveTimeMs;
        }

        @Override
        public RefStreamDefinition getRefStreamDefinition() {
            return refStreamDefinition;
        }

        public void initialise(final boolean overwriteExisting) {
            throwExceptionIfAlreadyInitialised();

            // TODO create processed streams entry if it doesn't exist with a state of IN_PROGRESS
            // TODO if it does exist update the update time

            beginTxn();
            this.initialised = true;

            final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    effectiveTimeMs,
                    RefDataProcessingInfo.ProcessingState.IN_PROGRESS);

            // TODO need to consider how to prevent multiple threads trying to load the same
            // ref data set at once

            // TODO do we want to overwrite the existing value or just update its state/lastAccessedTime?
            boolean didPutSucceed = processingInfoDb.put(
                    writeTxn, refStreamDefinition, refDataProcessingInfo, overwriteExisting);

            if (!overwriteExisting && !didPutSucceed) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Unable to create processing info entry as one already exists for key {}", refStreamDefinition));
            }
        }

        public void completeProcessing() {
            throwExceptionIfNotInitialised();
            beginTxnIfRequired();

            // Set the processing info record to COMPLETE and update the last update time
            processingInfoDb.updateProcessingState(
                    writeTxn, refStreamDefinition, RefDataProcessingInfo.ProcessingState.COMPLETE);
        }

        @Override
        public void setCommitInterval(final int maxPutsBeforeCommit) {
            Preconditions.checkArgument(maxPutsBeforeCommit >= 1);
            this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        }

        private void beginTxn() {
            if (writeTxn != null) {
                throw new RuntimeException("Transaction is already open");
            }
            this.writeTxn = lmdbEnvironment.txnWrite();
        }

        private void commit() {
            if (writeTxn != null) {
                try {
                    writeTxn.commit();
                    writeTxn = null;
                } catch (Exception e) {
                    throw new RuntimeException("Error committing write transaction", e);
                }
            }
        }

        @Override
        public void put(final MapDefinition mapDefinition,
                        final String key,
                        final RefDataValue refDataValue,
                        final boolean overwriteExistingValue) {
            Objects.requireNonNull(mapDefinition);
            Objects.requireNonNull(key);
            Objects.requireNonNull(refDataValue);

            throwExceptionIfNotInitialised();
            beginTxnIfRequired();

            final UID mapUid = getOrCreateUid(mapDefinition);

            //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
            final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, refDataValue);

            final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(mapUid, key, effectiveTimeMs);

            // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
            // do a get then optional put.
            boolean didPutSucceed = keyValueStoreDb.put(
                    writeTxn, keyValueStoreKey, valueStoreKey, overwriteExistingValue);

            if (!overwriteExistingValue && !didPutSucceed) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Entry already exists for key {} and overwriteExisting is false", keyValueStoreKey));
            }
        }


        @Override
        public void put(final MapDefinition mapDefinition,
                        final Range<Long> keyRange,
                        final RefDataValue refDataValue,
                        final boolean overwriteExistingValue) {
            Objects.requireNonNull(mapDefinition);
            Objects.requireNonNull(keyRange);
            Objects.requireNonNull(refDataValue);

            throwExceptionIfNotInitialised();
            beginTxnIfRequired();

            throwExceptionIfNotInitialised();
            beginTxnIfRequired();

            final UID mapUid = getOrCreateUid(mapDefinition);

            //get the ValueStoreKey for the RefDataValue (creating the entry if it doesn't exist)
            final ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, refDataValue);

            final RangeStoreKey rangeStoreKey = new RangeStoreKey(mapUid, effectiveTimeMs, keyRange);

            // assuming it is cheaper to just try the put and let LMDB handle duplicates rather than
            // do a get then optional put.
            boolean didPutSucceed = rangeStoreDb.put(
                    writeTxn, rangeStoreKey, valueStoreKey, overwriteExistingValue);

            if (!overwriteExistingValue && !didPutSucceed) {
                throw new RuntimeException(LambdaLogger.buildMessage(
                        "Entry already exists for key {} and overwriteExisting is false", rangeStoreKey));
            }
        }

        private UID getOrCreateUid(final MapDefinition mapDefinition) {
            // TODO we may have only just started a txn so if the UIDs in the cache wrap LMDB owned
            // buffers bad things could happen.  We could clear the cache on commit, or put clones
            // into the cache. Have added clone() call below, but needs testing.

            // get the UID for this mapDefinition, and as we should only have a handful of mapDefinitions
            // per loader it makes sense to cache the MapDefinition=>UID mappings on heap for quicker access.
            final UID uid = mapDefinitionToUIDMap.computeIfAbsent(mapDefinition, mapDef -> {
                LOGGER.debug("MapDefinition {} not found in local cache so getting it from the store");
                // cloning the UID in case we leave the txn
                return mapDefinitionUIDStore.getOrCreateUid(mapDef).clone();
            });
            return uid;
        }

        private void commitIfRequired() {
            putsCounter++;
            if (putsCounter >= maxPutsBeforeCommit) {
                //
                commit();
            }
        }

        private void beginTxnIfRequired() {
            if (writeTxn == null) {
                beginTxn();
            }
        }

        private void throwExceptionIfNotInitialised() {
            if (!initialised) {
                throw new RuntimeException("Loader not initialised");
            }
        }

        private void throwExceptionIfAlreadyInitialised() {
            if (initialised) {
                throw new RuntimeException("Loader is already initialised");
            }
        }

        @Override
        public void close() throws Exception {
            if (writeTxn != null) {
                writeTxn.commit();
                writeTxn.close();
            }
        }
    }

    public interface Factory {
        RefDataStore create(final Path dbDir, final long maxSize);
    }

}
