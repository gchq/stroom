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
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.OffHeapRefDataLoader.LoaderMode;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Class to migrate reference data for a specific stream from one
 * store to another. The data is left in the source store, but marked
 * as ready for purge.
 */
public class StoreMigrator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreMigrator.class);

    private final TaskContext taskContext;
    private final KeyValueStoreDb keyValueStoreDb;
    private final RangeStoreDb rangeStoreDb;
    private final ProcessingInfoDb processingInfoDb;
    private final ValueStore valueStore;
    private final MapDefinitionUIDStore mapDefinitionUIDStore;
    private final ReferenceDataConfig referenceDataConfig;
    private final RefDataOffHeapStore destinationStore;
    private final RefDataOffHeapStore sourceStore;
    private final long refStreamId;

    private MigrationState migrationState = null;

    public StoreMigrator(final long refStreamId,
                         final TaskContext taskContext,
                         final KeyValueStoreDb keyValueStoreDb,
                         final RangeStoreDb rangeStoreDb,
                         final ProcessingInfoDb processingInfoDb,
                         final ValueStore valueStore,
                         final MapDefinitionUIDStore mapDefinitionUIDStore,
                         final ReferenceDataConfig referenceDataConfig,
                         final RefDataOffHeapStore sourceStore,
                         final RefDataOffHeapStore destinationStore) {
        this.refStreamId = refStreamId;
        this.taskContext = taskContext;
        this.keyValueStoreDb = keyValueStoreDb;
        this.rangeStoreDb = rangeStoreDb;
        this.processingInfoDb = processingInfoDb;
        this.valueStore = valueStore;
        this.mapDefinitionUIDStore = mapDefinitionUIDStore;
        this.referenceDataConfig = referenceDataConfig;
        this.destinationStore = destinationStore;
        this.sourceStore = sourceStore;
        LOGGER.debug(() -> LogUtil.message("Initialising StoreMigrator for stream: {}, destinationStore: {}",
                refStreamId, destinationStore.getName()));
    }

    /**
     * Migrate the data for refStreamId into destinationStore
     */
    void migrate() {
        LOGGER.debug(() -> LogUtil.message("Migrating stream: {} into  destinationStore: {}",
                refStreamId, destinationStore.getName()));
        updateTaskContextInfoSupplier("Start");
        final DurationTimer timer = DurationTimer.start();
        try {
            final Predicate<RefStreamDefinition> refStreamDefinitionPredicate = refStreamDef ->
                    refStreamDef.getStreamId() == refStreamId;

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

                // RefStreamDefinition has variable width serialisation, so we have to scan to find what we are after
                final Optional<Entry<RefStreamDefinition, RefDataProcessingInfo>> optEntry =
                        sourceStore.getLmdbEnvironment().getWithReadTxn(readTxn ->
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

                    migrateRefStreamDef(refStreamDefinition, destinationStore);
                } else {
                    LOGGER.debug("No matching ref stream found");
                    wasMatchFound = false;
                }
            } while (wasMatchFound);

            LOGGER.info("Completed migration of ref stream {} into store {} in {}",
                    refStreamId, destinationStore.getName(), timer);

        } catch (final TaskTerminatedException e) {
            // Expected behaviour so just rethrow, stopping it being picked up by the other
            // catch block
            LOGGER.debug("Migration terminated, refStreamId: " + refStreamId, e);
            LOGGER.warn("Migration terminated, refStreamId: " + refStreamId);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(() -> "Migration of refStreamId " + refStreamId + " failed due to " + e.getMessage());
            throw e;
        }
    }

    private void migrateRefStreamDef(final RefStreamDefinition refStreamDefinition,
                                     final RefDataOffHeapStore destStore) {

        final DurationTimer timer = DurationTimer.start();
        // Here just to make code clearer
        // Need to lock both to ensure no one is trying to load the same stream into the dest store
        // or purge it from the source store. A deadlock is a bit of a worry as we are getting two locks
        // but this should be the only code that tries to lock the legacy store and a new one.
        updateTaskContextInfoSupplier("Waiting for destination store lock");
        destStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
            LOGGER.debug("Acquired destination lock in {}, refStreamDef: {}", timer, refStreamDefinition);
            updateTaskContextInfoSupplier("Waiting for source store lock");
            sourceStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
                LOGGER.debug("Acquired source lock in {}, refStreamDef: {}", timer, refStreamDefinition);

                // It is possible another thread beat us to the migration (or loaded it),
                // so check again for existence
                final boolean refStreamDefExists = destStore.exists(refStreamDefinition);
                if (refStreamDefExists) {
                    LOGGER.debug("refStreamDefinition already exists in destinationStore, nothing to do. " +
                            "refStreamDefinition: {}", refStreamDefinition);
                } else {
                    final Optional<RefDataProcessingInfo> optInfo = processingInfoDb.get(refStreamDefinition);
                    optInfo.ifPresent(info -> {
                        destStore.doWithLoaderUnlessComplete(
                                refStreamDefinition,
                                info.getEffectiveTimeEpochMs(),
                                refDataLoader -> {
                                    final OffHeapRefDataLoader destStoreLoader =
                                            (OffHeapRefDataLoader) refDataLoader;
                                    migrateRefStreamDef(refStreamDefinition, destStoreLoader);
                                });
                    });
                }
                // Now this stream is all migrated, mark as ready to purge so it is picked up in
                // purgePartialLoads
                LOGGER.info("Making ref stream available for purge: {}", refStreamDefinition);
                sourceStore.setProcessingState(refStreamDefinition, ProcessingState.READY_FOR_PURGE);
            });
        });
    }

    private void migrateRefStreamDef(final RefStreamDefinition refStreamDefinition,
                                     final OffHeapRefDataLoader destStoreLoader) {
        LOGGER.debug("Migrating entries for refStreamDefinition: {}", refStreamDefinition);
        try {
            // We are pulling data from an existing store, so duplicates have already been
            // dealt with
            destStoreLoader.initialise(false, LoaderMode.MIGRATE);
            final int maxCommits = referenceDataConfig.getMaxPutsBeforeCommit();

            try (final PooledByteBuffer uidPooledByteBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer();
                    final BatchingWriteTxn destBatchingWriteTxn = destinationStore.getLmdbEnvironment()
                            .openBatchingWriteTxn(maxCommits)) {

                final ByteBuffer uidByteBuffer = uidPooledByteBuffer.getByteBuffer();

                // Read txn on source store
                sourceStore.getLmdbEnvironment().doWithReadTxn(sourceReadTxn -> {
                    final List<MapDefinition> mapDefinitions = mapDefinitionUIDStore.getMapDefinitions(sourceReadTxn,
                            refStreamDefinition);

                    // Init the state for task info logging
                    migrationState = new MigrationState(mapDefinitions.size());

                    mapDefinitions.forEach(mapDefinition -> {
                        final UID mapUid = mapDefinitionUIDStore.getUid(sourceReadTxn, mapDefinition, uidByteBuffer)
                                .orElseThrow(() ->
                                        new RuntimeException("No UID for mapDefinition " + mapDefinition));

                        // As far as possible do the migration without any serialisation
                        migrateKeyValueEntries(
                                destStoreLoader,
                                destBatchingWriteTxn,
                                sourceReadTxn,
                                mapDefinition,
                                mapUid);

                        migrateRangeValueEntries(
                                destStoreLoader,
                                destBatchingWriteTxn,
                                sourceReadTxn,
                                mapDefinition,
                                mapUid);

                        migrationState.mapsMigrated.incrementAndGet();
                    });
                });
                destBatchingWriteTxn.commit();
            }

            destStoreLoader.completeProcessing(ProcessingState.COMPLETE);
        } catch (final Exception e) {
            destStoreLoader.completeProcessing(ProcessingState.FAILED);
            throw new RuntimeException(e);
        }

    }

    private void migrateKeyValueEntries(final OffHeapRefDataLoader destStoreLoader,
                                        final BatchingWriteTxn destBatchingWriteTxn,
                                        final Txn<ByteBuffer> readTxn,
                                        final MapDefinition mapDefinition,
                                        final UID mapUid) {
        LOGGER.debug("Migrating Key/Value entries for {}, {}", mapDefinition, migrationState);
        // Loop over all KV entries for this map name in this stream
        updateTaskContextInfoSupplier("Migrating key/value entries");
        keyValueStoreDb.forEachEntryAsBytes(readTxn, mapUid, keyValBuffers -> {
            final ByteBuffer keyValueStoreKeyBuffer = keyValBuffers.key();
            final ByteBuffer valueStoreKeyBuffer = keyValBuffers.val();

            final Byte typeId = valueStore.getTypeId(readTxn, valueStoreKeyBuffer);
            Objects.requireNonNull(typeId, "Every entry should have a corresponding value meta");
            final ByteBuffer refDataValueBuffer = valueStore.getAsBytes(readTxn, valueStoreKeyBuffer)
                    .orElseThrow(() -> new RuntimeException(
                            "Every entry should have a corresponding value"));

            destStoreLoader.migrateKeyEntry(
                    destBatchingWriteTxn,
                    mapDefinition,
                    keyValueStoreKeyBuffer,
                    typeId,
                    refDataValueBuffer);
            migrationState.entriesMigrated.incrementAndGet();
            destBatchingWriteTxn.commitIfRequired();
        });
        LOGGER.debug("Migrated Key/Value entries for {}, {}", mapDefinition, migrationState);
    }

    private void migrateRangeValueEntries(final OffHeapRefDataLoader destStoreLoader,
                                          final BatchingWriteTxn destBatchingWriteTxn,
                                          final Txn<ByteBuffer> sourceReadTxn,
                                          final MapDefinition mapDefinition,
                                          final UID mapUid) {
        LOGGER.debug("Migrating Range/Value entries for {}, {}", mapDefinition, migrationState);
        // Loop over all range:value entries for this map name in this stream
        updateTaskContextInfoSupplier("Migrating range/value entries");
        rangeStoreDb.forEachEntryAsBytes(sourceReadTxn, mapUid, keyValBuffers -> {
            final ByteBuffer rangeStoreKeyBuffer = keyValBuffers.key();
            final ByteBuffer valueStoreKeyBuffer = keyValBuffers.val();

            final Byte typeId = valueStore.getTypeId(sourceReadTxn, valueStoreKeyBuffer);
            Objects.requireNonNull(typeId, "Every entry should have a corresponding value meta");
            final ByteBuffer refDataValueBuffer = valueStore.getAsBytes(sourceReadTxn, valueStoreKeyBuffer)
                    .orElseThrow(() -> new RuntimeException(
                            "Every entry should have a corresponding value"));

            destStoreLoader.migrateRangeEntry(
                    destBatchingWriteTxn,
                    mapDefinition,
                    rangeStoreKeyBuffer,
                    typeId,
                    refDataValueBuffer);
            migrationState.entriesMigrated.incrementAndGet();
            destBatchingWriteTxn.commitIfRequired();
        });
        LOGGER.debug("Migrated Range/Value entries for {}, {}", mapDefinition, migrationState);
    }


    private void updateTaskContextInfoSupplier(final String extraText) {
        taskContext.info(() -> {
            final String extraTextArg = extraText != null
                    ? " - " + extraText
                    : "";
            final String stateArg = migrationState != null
                    ? LogUtil.message(" - Migrated {} of {} maps, total entries migrated: {}",
                    migrationState.mapsMigrated.get(),
                    migrationState.totalMaps,
                    migrationState.entriesMigrated.get())
                    : "";

            return LogUtil.message("Migrating legacy ref data for reference stream {} into store {}{}{}",
                    refStreamId,
                    destinationStore.getName(),
                    extraTextArg,
                    stateArg);
        });
    }

    // --------------------------------------------------------------------------------


    private static class MigrationState {

        final int totalMaps;
        // Have to be volatile as they will be read by the Server tasks screen
        AtomicInteger mapsMigrated = new AtomicInteger(0);
        AtomicInteger entriesMigrated = new AtomicInteger(0);

        private MigrationState(final int totalMaps) {
            this.totalMaps = totalMaps;
        }

        @Override
        public String toString() {
            return "MigrationState{" +
                    "totalMaps=" + totalMaps +
                    ", mapsMigrated=" + mapsMigrated +
                    ", entriesMigrated=" + entriesMigrated +
                    '}';
        }
    }
}
