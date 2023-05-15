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

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ChildData;
import stroom.dashboard.expression.v1.CountPrevious;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.LmdbEnvFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;
import stroom.util.shared.time.SimpleDuration;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Provider;

public class LmdbDataStore implements DataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStore.class);

    private static final long COMMIT_FREQUENCY_MS = 10000;

    private final LmdbEnv lmdbEnv;
    private final Dbi<ByteBuffer> dbi;

    private final FieldExpressionMatcher fieldExpressionMatcher;
    private final ExpressionOperator valueFilter;
    private final ValueReferenceIndex valueReferenceIndex;
    private final CompiledField[] compiledFieldArray;
    private final CompiledSorter<Item>[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final boolean limitResultCount;
    private final AtomicLong resultCount = new AtomicLong();

    private final AtomicBoolean hasEnoughData = new AtomicBoolean();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private final LmdbWriteQueue queue;
    private final CountDownLatch complete = new CountDownLatch(1);
    private final CompletionState completionState = new CompletionStateImpl(this, complete);
    private final QueryKey queryKey;
    private final String componentId;
    private final FieldIndex fieldIndex;
    private final boolean producePayloads;
    private final ErrorConsumer errorConsumer;
    private final LmdbRowKeyFactory lmdbRowKeyFactory;
    private final LmdbRowValueFactory lmdbRowValueFactory;
    private final KeyFactoryConfig keyFactoryConfig;
    private final KeyFactory keyFactory;
    private final LmdbPayloadCreator payloadCreator;
    private final TransferState transferState = new TransferState();
    private final ValHasher valHasher;

    private final Serialisers serialisers;

    private final WindowSupport windowSupport;


    private final int maxPutsBeforeCommit;
    private boolean storeLatestEventReference;
    private int streamIdFieldIndex = -1;
    private int eventIdFieldIndex = -1;

    private final StoredValueKeyFactory storedValueKeyFactory;


    LmdbDataStore(final Serialisers serialisers,
                  final LmdbEnvFactory lmdbEnvFactory,
                  final AbstractResultStoreConfig resultStoreConfig,
                  final QueryKey queryKey,
                  final String componentId,
                  final TableSettings tableSettings,
                  final FieldIndex fieldIndex,
                  final Map<String, String> paramMap,
                  final DataStoreSettings dataStoreSettings,
                  final Provider<Executor> executorProvider,
                  final ErrorConsumer errorConsumer) {
        this.serialisers = serialisers;
        this.maxResults = dataStoreSettings.getMaxResults();
        this.queryKey = queryKey;
        this.componentId = componentId;
        this.fieldIndex = fieldIndex;
        this.producePayloads = dataStoreSettings.isProducePayloads();
        this.errorConsumer = errorConsumer;

        // Add stream id and event id fields if we need them.
        if (dataStoreSettings.isRequireStreamIdValue() && dataStoreSettings.isRequireEventIdValue()) {
            streamIdFieldIndex = fieldIndex.getStreamIdFieldIndex();
            eventIdFieldIndex = fieldIndex.getEventIdFieldIndex();
            storeLatestEventReference = true;
        }

        this.windowSupport = new WindowSupport(tableSettings);
        final TableSettings modifiedTableSettings = windowSupport.getTableSettings();
        final List<Field> fields = modifiedTableSettings.getFields();
        queue = new LmdbWriteQueue(resultStoreConfig.getValueQueueSize());
        valueFilter = modifiedTableSettings.getValueFilter();
        fieldExpressionMatcher = new FieldExpressionMatcher(fields);
        final CompiledFields compiledFields = CompiledFields.create(fields, fieldIndex, paramMap);
        this.compiledFieldArray = compiledFields.getCompiledFields();
        valueReferenceIndex = compiledFields.getValueReferenceIndex();
        compiledDepths = new CompiledDepths(this.compiledFieldArray, modifiedTableSettings.showDetail());
        compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), this.compiledFieldArray);
        keyFactoryConfig = new KeyFactoryConfigImpl(this.compiledFieldArray, compiledDepths, dataStoreSettings);
        keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        valHasher = new ValHasher(serialisers.getOutputFactory(), errorConsumer);
        lmdbRowKeyFactory = LmdbRowKeyFactoryFactory.create(keyFactory, keyFactoryConfig, compiledDepths, valHasher);
        lmdbRowValueFactory = new LmdbRowValueFactory(
                valueReferenceIndex,
                serialisers.getOutputFactory(),
                errorConsumer);
        payloadCreator = new LmdbPayloadCreator(
                queryKey,
                this,
                resultStoreConfig,
                lmdbRowKeyFactory);
        maxPutsBeforeCommit = resultStoreConfig.getMaxPutsBeforeCommit();

        this.lmdbEnv = lmdbEnvFactory.builder(resultStoreConfig.getLmdbConfig())
                .withSubDirectory(dataStoreSettings.getSubDirectory())
                .withMaxDbCount(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();
        this.dbi = lmdbEnv.openDbi(queryKey + "_" + componentId);

        // Find out if we have any sorting.
        boolean hasSort = false;
        for (final CompiledSorter<Item> sorter : compiledSorters) {
            if (sorter != null) {
                hasSort = true;
                break;
            }
        }

        // Determine if we are going to limit the result count.
        limitResultCount = maxResults != null && !hasSort && !compiledDepths.hasGroup();

        // Start transfer loop.
        executorProvider.get().execute(this::transfer);

        storedValueKeyFactory = new StoredValueKeyFactory(compiledDepths, compiledFieldArray, keyFactoryConfig);
    }

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    @Override
    public void add(final Val[] values) {
        // Filter incoming data.
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        Map<String, Object> fieldIdToValueMap = null;
        for (final CompiledField compiledField : compiledFieldArray) {
            final Generator generator = compiledField.getGenerator();
            if (generator != null) {
                final CompiledFilter compiledFilter = compiledField.getCompiledFilter();
                String string = null;
                if (compiledFilter != null || valueFilter != null) {
                    generator.set(values, storedValues);
                    string = generator.eval(storedValues, null).toString();
                }

                if (compiledFilter != null && !compiledFilter.match(string)) {
                    // We want to exclude this item so get out of this method ASAP.
                    return;
                } else if (valueFilter != null) {
                    if (fieldIdToValueMap == null) {
                        fieldIdToValueMap = new HashMap<>();
                    }
                    fieldIdToValueMap.put(compiledField.getField().getName(),
                            string);
                }
            }
        }

        if (fieldIdToValueMap != null) {
            // If the value filter doesn't match then get out of here now.
            if (!fieldExpressionMatcher.match(fieldIdToValueMap, valueFilter)) {
                return;
            }
        }

        // Now add the rows if we aren't filtering.
        if (windowSupport.getOffsets() != null) {
            int iteration = 0;
            for (SimpleDuration offset : windowSupport.getOffsets()) {
                final Val[] modifiedValues = windowSupport.addWindow(fieldIndex, values, offset);
                addInternal(modifiedValues, iteration);
                iteration++;
            }
        } else {
            addInternal(values, -1);
        }
    }

    private void addInternal(final Val[] values,
                             final int iteration) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_ADD);
        LOGGER.trace(() -> "add() called for " + values.length + " values");
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        // Get a reference to the current event so we can keep track of what we have stored data for.
        final CurrentDbState currentDbState = createCurrentDbState(values);

        Key parentKey = Key.ROOT_KEY;
        long parentGroupHash = 0;

        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            for (int fieldIndex = 0; fieldIndex < compiledFieldArray.length; fieldIndex++) {
                final CompiledField compiledField = compiledFieldArray[fieldIndex];
                final Generator generator = compiledField.getGenerator();

                // If we need a value at this level then set the raw values.
                if (valueIndices[fieldIndex] ||
                        fieldIndex == keyFactoryConfig.getTimeFieldIndex()) {
                    if (iteration != -1) {
                        if (generator instanceof CountPrevious.Gen gen) {
                            if (gen.getIteration() == iteration) {
                                generator.set(values, storedValues);
                            }
                        } else {
                            generator.set(values, storedValues);
                        }
                    } else {
                        generator.set(values, storedValues);
                    }
                }
            }

            final Val[] groupValues = storedValueKeyFactory.getGroupValues(depth, storedValues);
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            final long groupHash = valHasher.hash(groupValues);
            final Key key = storedValueKeyFactory.createKey(parentKey, timeMs, groupValues);

            final ByteBuffer rowKey = lmdbRowKeyFactory.create(depth, parentGroupHash, groupHash, timeMs);
            final ByteBuffer rowValue = lmdbRowValueFactory.create(storedValues);

            put(new LmdbKV(currentDbState, rowKey, rowValue));
            parentGroupHash = groupHash;
            parentKey = key;
        }
    }

    void put(final LmdbQueueItem queueItem) {
        LOGGER.trace(() -> "put");
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_PUT);

        // Some searches can be terminated early if the user is not sorting or grouping.
        boolean allow = true;
        if (limitResultCount) {
            // No sorting or grouping, so we can stop the search as soon as we have the number of results requested by
            // the client
            allow = !hasEnoughData.get();
            if (allow) {
                final long currentResultCount = totalResultCount.getAndIncrement();
                if (currentResultCount >= maxResults.size(0)) {
                    allow = false;

                    // If we have enough data then we can stop transferring data and complete.
                    if (hasEnoughData.compareAndSet(false, true)) {
                        completionState.signalComplete();
                    }
                }
            }
        }

        if (allow) {
            doPut(queueItem);
        }
    }

    private void doPut(final LmdbQueueItem queueItem) {
        try {
            queue.put(queueItem);
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
    }

    private void transfer() {
        Metrics.measure("Transfer", () -> {
            transferState.setThread(Thread.currentThread());

            CurrentDbState currentDbState = null;
            try (final BatchingWriteTxn writeTxn = lmdbEnv.openBatchingWriteTxn(0)) {
                long lastCommitMs = System.currentTimeMillis();
                long uncommittedCount = 0;

                try {
                    while (!transferState.isTerminated()) {
                        LOGGER.trace(() -> "Transferring");
                        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_QUEUE_POLL);
                        final LmdbQueueItem queueItem = queue.poll(1, TimeUnit.SECONDS);

                        if (queueItem != null) {
                            if (queueItem instanceof final LmdbKV lmdbKV) {
                                currentDbState = lmdbKV.getCurrentDbState();
                                insert(writeTxn, dbi, lmdbKV);
                                uncommittedCount++;
                            } else {
                                if (queueItem instanceof final Sync sync) {
                                    commit(writeTxn, currentDbState);
                                    sync.sync();
                                } else if (queueItem instanceof final DeleteCommand deleteCommand) {
                                    delete(writeTxn, deleteCommand);
                                }
                            }
                        }

                        if (producePayloads && payloadCreator.isEmpty()) {
                            // Commit
                            LOGGER.debug(() -> "Committing for new payload");

                            commit(writeTxn, currentDbState);
                            lastCommitMs = System.currentTimeMillis();
                            uncommittedCount = 0;

                            // Create payload and clear the DB.
                            payloadCreator.addPayload(writeTxn, dbi, false);

                        } else if (uncommittedCount > 0) {
                            final long count = uncommittedCount;
                            if (count >= maxPutsBeforeCommit ||
                                    lastCommitMs < System.currentTimeMillis() - COMMIT_FREQUENCY_MS) {

                                // Commit
                                LOGGER.debug(() -> {
                                    if (count >= maxPutsBeforeCommit) {
                                        return "Committing for max puts " + maxPutsBeforeCommit;
                                    } else {
                                        return "Committing for elapsed time";
                                    }
                                });
                                commit(writeTxn, currentDbState);
                                lastCommitMs = System.currentTimeMillis();
                                uncommittedCount = 0;
                            }
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.trace(e::getMessage, e);
                    // Keep interrupting this thread.
                    Thread.currentThread().interrupt();
                } catch (final CompleteException e) {
                    LOGGER.debug(() -> "Complete");
                    LOGGER.trace(e::getMessage, e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    errorConsumer.add(e);
                }

                if (!transferState.isTerminated() && uncommittedCount > 0) {
                    LOGGER.debug(() -> "Final commit");
                    commit(writeTxn, currentDbState);
                }

                // Create final payloads and ensure they are all delivered before we complete.
                if (!transferState.isTerminated() && producePayloads) {
                    LOGGER.debug(() -> "Producing final payloads");
                    // Create payload and clear the DB.
                    boolean finalPayload = false;
                    while (!finalPayload) {
                        finalPayload = payloadCreator.addPayload(writeTxn, dbi, true);
                    }
                    // Make sure we end with an empty payload to indicate completion.
                    // Adding a final empty payload to the queue ensures that a consuming node will have to request the
                    // payload from the queue before we complete.
                    LOGGER.debug(() -> "Final payload");
                    payloadCreator.finalPayload();
                }

            } catch (final Throwable e) {
                LOGGER.error(e::getMessage, e);
                errorConsumer.add(e);
            } finally {
                // Ensure we complete.
                complete.countDown();
                LOGGER.debug(() -> "Finished transfer while loop");
                transferState.setThread(null);
            }
        });
    }

    private void delete(final BatchingWriteTxn writeTxn,
                        final DeleteCommand deleteCommand) {
        final KeyRange<ByteBuffer> keyRange =
                lmdbRowKeyFactory.createChildKeyRange(deleteCommand.getParentKey(), deleteCommand.getTimeFilter());
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(writeTxn.getTxn(), keyRange)) {
            for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                final ByteBuffer keyBuffer = kv.key();
                if (keyBuffer.limit() > 1) {
                    dbi.delete(writeTxn.getTxn(), keyBuffer);
                }
            }
        }
        writeTxn.commit();
    }

    private void commit(final BatchingWriteTxn writeTxn,
                        final CurrentDbState currentDbState) {
        putCurrentDbState(writeTxn, currentDbState);
        writeTxn.commit();
    }

    private void insert(final BatchingWriteTxn writeTxn,
                        final Dbi<ByteBuffer> dbi,
                        final LmdbKV lmdbKV) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_INSERT);
        Metrics.measure("Insert", () -> {
            try {
                LOGGER.trace(() -> "insert");

                // Just try to put first.
                final boolean success = put(
                        writeTxn,
                        dbi,
                        lmdbKV.getRowKey(),
                        lmdbKV.getRowValue(),
                        PutFlags.MDB_NOOVERWRITE);
                if (success) {
                    resultCount.incrementAndGet();

                } else {
                    final int depth = lmdbRowKeyFactory.getDepth(lmdbKV);
                    if (lmdbRowKeyFactory.isGroup(depth)) {
                        final StoredValues newStoredValues =
                                valueReferenceIndex.read(lmdbKV.getRowValue().duplicate());
                        final Val[] newGroupValues
                                = storedValueKeyFactory.getGroupValues(depth, newStoredValues);

                        // Get the existing entry for this key.
                        final ByteBuffer existingValueBuffer = dbi.get(writeTxn.getTxn(), lmdbKV.getRowKey());
                        final ByteBuffer newValueBuffer = lmdbRowValueFactory.useOutput(output -> {
                            boolean merged = false;
                            while (existingValueBuffer.remaining() > 0) {
                                final int startPos = existingValueBuffer.position();
                                final StoredValues existingStoredValues =
                                        valueReferenceIndex.read(existingValueBuffer);
                                final int endPos = existingValueBuffer.position();
                                final Val[] existingGroupValues
                                        = storedValueKeyFactory.getGroupValues(depth, existingStoredValues);

                                // If this is the same value then update it and reinsert.
                                if (Arrays.equals(existingGroupValues, newGroupValues)) {
                                    for (final CompiledField compiledField : compiledFieldArray) {
                                        compiledField.getGenerator().merge(existingStoredValues, newStoredValues);
                                    }

                                    LOGGER.trace(() -> "Merging combined value to output");
                                    valueReferenceIndex.write(existingStoredValues, output);

                                    // Copy any remaining values.
                                    output.writeByteBuffer(existingValueBuffer);
                                    merged = true;
                                } else {
                                    LOGGER.debug(() -> "Copying value to output");
                                    output.writeByteBuffer(existingValueBuffer.slice(startPos, endPos - startPos));
                                }
                            }

                            // Append if we didn't merge.
                            if (!merged) {
                                LOGGER.debug(() -> "Appending value to output");
                                output.writeByteBuffer(lmdbKV.getRowValue());
                                resultCount.incrementAndGet();
                            }
                        });

                        final boolean ok = put(writeTxn, dbi, lmdbKV.getRowKey(), newValueBuffer);
                        if (!ok) {
                            LOGGER.debug(() -> "Unable to update");
                            throw new RuntimeException("Unable to update");
                        }

                    } else {
                        // We do not expect a key collision here.
                        LOGGER.debug(() -> "Unexpected collision");
                        throw new RuntimeException("Unexpected collision");
                    }
                }

            } catch (final RuntimeException e) {
                if (LOGGER.isTraceEnabled()) {
                    // Only evaluate queue item value in trace as it can be expensive.
                    LOGGER.trace(() -> "Error putting " + lmdbKV + " (" + e.getMessage() + ")", e);
                } else {
                    LOGGER.debug(() -> "Error putting queueItem (" + e.getMessage() + ")", e);
                }

                final RuntimeException exception =
                        new RuntimeException("Error putting queueItem (" + e.getMessage() + ")", e);
                errorConsumer.add(exception);

                // Treat all errors as fatal so complete.
                completionState.signalComplete();

                throw exception;
            }
        });
    }

    private boolean put(final BatchingWriteTxn writeTxn,
                        final Dbi<ByteBuffer> dbi,
                        final ByteBuffer key,
                        final ByteBuffer val,
                        final PutFlags... flags) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_DBI_PUT);
            return dbi.put(writeTxn.getTxn(), key, val, flags);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            errorConsumer.add(e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Get data from the store
     * Synchronised with clear to prevent a shutdown happening while reads are going on.
     *
     * @param consumer Consumer for the data.
     */
    @Override
    public synchronized void getData(final Consumer<Data> consumer) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET);
        LOGGER.trace(() -> "getData()");

        if (lmdbEnv.isClosed()) {
            // If we query LMDB after the env has been closed then we are likely to crash the JVM
            // see https://github.com/lmdbjava/lmdbjava/issues/185
            LOGGER.debug(() -> "getData() called (queryKey =" +
                    queryKey +
                    ", componentId=" +
                    componentId +
                    ") after store has been shut down");
        } else {
            lmdbEnv.doWithReadTxn(readTxn ->
                    Metrics.measure("getData", () ->
                            consumer.accept(new LmdbData(
                                    lmdbRowKeyFactory,
                                    storedValueKeyFactory,
                                    dbi,
                                    readTxn,
                                    compiledFieldArray,
                                    compiledSorters,
                                    maxResults,
                                    queryKey,
                                    valueReferenceIndex))));
        }
    }

    public synchronized void close() {
        LOGGER.debug(() -> "close called");
        LOGGER.trace(() -> "close()", new RuntimeException("close"));
        if (shutdown.compareAndSet(false, true)) {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_CLEAR);

            // Let the transfer loop know it should stop ASAP.
            transferState.terminate();

            // Clear the queue.
            queue.clear();

            // If the transfer loop is waiting on new queue items ensure it loops once more.
            completionState.signalComplete();

            // Wait for transferring to stop.
            try {
                LOGGER.debug(() -> "Waiting for transfer to stop");
                completionState.awaitCompletion();
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }

            try {
                dbi.close();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                errorConsumer.add(e);
            }

            try {
                lmdbEnv.close();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                errorConsumer.add(e);
            }
        }
    }

    /**
     * Clear the data store.
     * Synchronised with get() to prevent a shutdown happening while reads are going on.
     */
    @Override
    public synchronized void clear() {
        try {
            close();
            try {
                lmdbEnv.delete();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                errorConsumer.add(e);
            }
        } finally {
            resultCount.set(0);
            totalResultCount.set(0);
        }
    }

    /**
     * Get the completion state associated with receiving all search results and having added them to the store
     * successfully.
     *
     * @return The search completion state for the data store.
     */
    @Override
    public CompletionState getCompletionState() {
        return completionState;
    }

    /**
     * Read items from the supplied input and transfer them to the data store.
     *
     * @param input The input to read.
     */
    @Override
    public void readPayload(final Input input) {
        // Return false if we aren't happy to accept any more data.
        payloadCreator.readPayload(input);
    }

    /**
     * Write data from the data store to an output removing them from the datastore as we go as they will be transferred
     * to another store.
     *
     * @param output The output to write to.
     */
    @Override
    public void writePayload(final Output output) {
        if (!producePayloads) {
            throw new RuntimeException("Not producing payloads");
        }
        payloadCreator.writePayload(output);
    }

    @Override
    public long getByteSize() {
        final AtomicLong total = new AtomicLong();
        try {
            final Path dir = lmdbEnv.getLocalDir();
            try (final Stream<Path> stream = Files.walk(dir)) {
                stream.forEach(path -> {
                    try {
                        if (Files.isRegularFile(path)) {
                            total.addAndGet(Files.size(path));
                        }
                    } catch (final IOException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return total.get();
    }

    @Override
    public Serialisers getSerialisers() {
        return serialisers;
    }

    @Override
    public KeyFactory getKeyFactory() {
        return keyFactory;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    private CurrentDbState createCurrentDbState(final Val[] values) {
        if (storeLatestEventReference) {
            if (streamIdFieldIndex >= 0 && eventIdFieldIndex >= 0) {
                final Val streamId = values[streamIdFieldIndex];
                final Val eventId = values[eventIdFieldIndex];
                final Val eventTime = values[fieldIndex.getWindowTimeFieldPos()];

                if (streamId != null && eventId != null) {
                    return new CurrentDbState(streamId.toLong(), eventId.toLong(), eventTime.toLong());
                }
            }
        }
        return null;
    }

    private void putCurrentDbState(final BatchingWriteTxn writeTxn, final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            final ByteBuffer keyBuffer = LmdbRowKeyFactoryFactory.DB_STATE_KEY;
            ByteBuffer valueBuffer = ByteBuffer.allocateDirect(Long.BYTES + Long.BYTES + Long.BYTES);
            valueBuffer.putLong(currentDbState.getStreamId());
            valueBuffer.putLong(currentDbState.getEventId());
            valueBuffer.putLong(currentDbState.getLastEventTime());
            valueBuffer = valueBuffer.flip();

            final boolean success = put(
                    writeTxn,
                    dbi,
                    keyBuffer,
                    valueBuffer);
            if (!success) {
                LOGGER.error("Unable to store event ref");
            }
        }
    }

    private synchronized CurrentDbState getCurrentDbState() {
        if (!storeLatestEventReference) {
            return null;
        }

        final AtomicReference<CurrentDbState> currentDbStateAtomicReference = new AtomicReference<>();
        final KeyRange<ByteBuffer> keyRange = LmdbRowKeyFactoryFactory.DB_STATE_KEY_RANGE;
        lmdbEnv.doWithReadTxn(readTxn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(
                    readTxn,
                    keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                if (iterator.hasNext()) {
                    final KeyVal<ByteBuffer> keyVal = iterator.next();
                    final ByteBuffer key = keyVal.key();
                    if (key.limit() == 1 & key.equals(LmdbRowKeyFactoryFactory.DB_STATE_KEY)) {
                        final ByteBuffer val = keyVal.val();
                        final long streamId = val.getLong(0);
                        final long eventId = val.getLong(Long.BYTES);
                        final long lastEventTime = val.getLong(Long.BYTES + Long.BYTES);
                        currentDbStateAtomicReference.set(new CurrentDbState(streamId, eventId, lastEventTime));
                    }
                }
            }
        });

        return currentDbStateAtomicReference.get();
    }

    public CurrentDbState sync() {
        final CurrentDbState currentDbState;
        try {
            // Synchronise the puts so we know all current items have been added to LMDB.
            final CountDownLatch complete = new CountDownLatch(1);
            put((Sync) complete::countDown);
            complete.await();

            // Get the current DB state.
            currentDbState = getCurrentDbState();
            LOGGER.info("Current Db State: " + currentDbState);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return currentDbState;
    }

    public void delete(final DeleteCommand deleteCommand) {
        put(deleteCommand);
    }

    private static class LmdbData implements Data {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbData.class);

        private final LmdbRowKeyFactory lmdbRowKeyFactory;
        private final StoredValueKeyFactory storedValueKeyFactory;
        private final Dbi<ByteBuffer> dbi;
        private final Txn<ByteBuffer> readTxn;
        private final CompiledField[] compiledFields;
        private final CompiledSorter<Item>[] compiledSorters;
        private final Sizes maxResults;
        private final QueryKey queryKey;
        private final ValueReferenceIndex valueReferenceIndex;

        public LmdbData(final LmdbRowKeyFactory lmdbRowKeyFactory,
                        final StoredValueKeyFactory storedValueKeyFactory,
                        final Dbi<ByteBuffer> dbi,
                        final Txn<ByteBuffer> readTxn,
                        final CompiledField[] compiledFields,
                        final CompiledSorter<Item>[] compiledSorters,
                        final Sizes maxResults,
                        final QueryKey queryKey,
                        final ValueReferenceIndex valueReferenceIndex) {
            this.lmdbRowKeyFactory = lmdbRowKeyFactory;
            this.storedValueKeyFactory = storedValueKeyFactory;
            this.dbi = dbi;
            this.readTxn = readTxn;
            this.compiledFields = compiledFields;
            this.compiledSorters = compiledSorters;
            this.maxResults = maxResults;
            this.queryKey = queryKey;
            this.valueReferenceIndex = valueReferenceIndex;
        }

        /**
         * Get child items from the data store for the provided parent key.
         * Synchronised with clear to prevent a shutdown happening while reads are going on.
         *
         * @param parentKey The parent key to get child items for.
         * @return The child items for the parent key.
         */
        @Override
        public Items get(final Key parentKey, final TimeFilter timeFilter) {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET);
            LOGGER.trace(() -> "get() called for parentKey: " + parentKey);

            return Metrics.measure("get", () -> {
                final int childDepth = parentKey.getChildDepth();
                final int trimmedSize = maxResults.size(childDepth);
                return getChildren(parentKey, timeFilter, childDepth, trimmedSize, false);
            });
        }

        private ItemsImpl getChildren(final Key parentKey,
                                      final TimeFilter timeFilter,
                                      final int childDepth,
                                      final int trimmedSize,
                                      final boolean trimTop) {
            try {
                SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET_CHILDREN);
                // If we don't have any children at the requested depth then return an empty list.
                if (compiledSorters.length <= childDepth) {
                    return ItemsImpl.EMPTY;
                }

                final ItemsImpl list = new ItemsImpl(10);

                final KeyRange<ByteBuffer> keyRange = lmdbRowKeyFactory.createChildKeyRange(parentKey, timeFilter);
                final int maxSize;
                if (trimmedSize < Integer.MAX_VALUE / 2) {
                    maxSize = Math.max(1000, trimmedSize * 2);
                } else {
                    maxSize = Integer.MAX_VALUE;
                }
                final CompiledSorter<Item> sorter = compiledSorters[childDepth];

                boolean trimmed = true;
                boolean addMore = true;

                try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(
                        readTxn,
                        keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                    while (iterator.hasNext()
                            && addMore
                            && !Thread.currentThread().isInterrupted()) {

                        final KeyVal<ByteBuffer> keyVal = iterator.next();

                        // All valid keys are more than a single byte long. Single byte keys are used to store db info.
                        if (keyVal.key().limit() > 1) {
                            final ByteBuffer valueBuffer = keyVal.val();
                            while (valueBuffer.remaining() > 0 && addMore) {

//                            if (key.getParent().equals(parentKey)) {
                                final StoredValues storedValues =
                                        valueReferenceIndex.read(valueBuffer);
                                final Key key = storedValueKeyFactory.createKey(parentKey, storedValues);

                                list.add(new ItemImpl(this, key, timeFilter, storedValues));
                                if (list.size >= trimmedSize && sorter == null) {
                                    // Stop without sorting etc.
                                    addMore = false;

                                } else {
                                    trimmed = false;
                                    if (list.size() > maxSize) {
                                        list.sortAndTrim(sorter, trimmedSize, trimTop);
                                        trimmed = true;
                                    }
                                }
//                            }
                            }
                        }
                    }
                }

                if (!trimmed) {
                    list.sortAndTrim(sorter, trimmedSize, trimTop);
                }

                return list;
            } catch (final UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }

        private long countChildren(final Key parentKey,
                                   final int depth) {
            long count = 0;

            // If we don't have any children at the requested depth then return 0.
            if (compiledSorters.length <= depth) {
                return 0;
            }

            final KeyRange<ByteBuffer> keyRange = lmdbRowKeyFactory.createChildKeyRange(parentKey);

            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn, keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                while (iterator.hasNext()
                        && !Thread.currentThread().isInterrupted()) {
                    count++;
                }
            }

            return count;
        }
    }

    private static class ItemsImpl implements Items {

        private static final ItemsImpl EMPTY = new ItemsImpl(0);

        private final int minArraySize;
        private ItemImpl[] array;
        private int size;

        public ItemsImpl(final int minArraySize) {
            this.minArraySize = minArraySize;
            array = new ItemImpl[minArraySize];
        }

        void sortAndTrim(final CompiledSorter<Item> sorter,
                         final int trimmedSize,
                         final boolean trimTop) {
            if (sorter != null && size > 0) {
                Arrays.sort(array, 0, size, sorter);
            }
            if (size > trimmedSize) {
                final int len = Math.max(minArraySize, trimmedSize);
                final ItemImpl[] newArray = new ItemImpl[len];
                if (trimTop) {
                    System.arraycopy(array, size - trimmedSize, newArray, 0, trimmedSize);
                } else {
                    System.arraycopy(array, 0, newArray, 0, trimmedSize);
                }
                array = newArray;
                size = trimmedSize;
            }
        }

        void add(final ItemImpl item) {
            if (array.length <= size) {
                final ItemImpl[] newArray = new ItemImpl[size * 2];
                System.arraycopy(array, 0, newArray, 0, array.length);
                array = newArray;
            }
            array[size++] = item;
        }

        @Override
        public ItemImpl get(final int index) {
            return array[index];
        }

        @Override
        public int size() {
            return size;
        }

        public Iterable<StoredValues> getStoredValueIterable() {
            return () -> new Iterator<>() {
                private int pos = 0;

                @Override
                public boolean hasNext() {
                    return size > pos;
                }

                @Override
                public StoredValues next() {
                    return array[pos++].storedValues;
                }
            };
        }

        @Override
        public Iterable<Item> getIterable() {
            return () -> new Iterator<>() {
                private int pos = 0;

                @Override
                public boolean hasNext() {
                    return size > pos;
                }

                @Override
                public ItemImpl next() {
                    return array[pos++];
                }
            };
        }
    }

    public static class ItemImpl implements Item {

        private final LmdbData data;
        private final Key key;
        private final TimeFilter timeFilter;
        private final StoredValues storedValues;
        private final Val[] cachedValues;

        public ItemImpl(final LmdbData data,
                        final Key key,
                        final TimeFilter timeFilter,
                        final StoredValues storedValues) {
            this.data = data;
            this.key = key;
            this.timeFilter = timeFilter;
            this.storedValues = storedValues;
            this.cachedValues = new Val[data.compiledFields.length];
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index, final boolean evaluateChildren) {
            Val val = cachedValues[index];
            if (val == null) {
                val = createValue(index);
                cachedValues[index] = val;
            }
            return val;
        }

        private Val createValue(final int index) {
            Val val;
            final Generator generator = data.compiledFields[index].getGenerator();
            if (key.isGrouped()) {
                final Supplier<ChildData> childDataSupplier = () -> {
                    // If we don't have any children at the requested depth then return null.
                    if (data.compiledSorters.length <= key.getChildDepth()) {
                        return null;
                    }

                    return new ChildData() {
                        @Override
                        public StoredValues first() {
                            return singleValue(1, false);
                        }

                        @Override
                        public StoredValues last() {
                            return singleValue(1, true);
                        }

                        @Override
                        public StoredValues nth(final int pos) {
                            return singleValue(pos + 1, false);
                        }

                        @Override
                        public Iterable<StoredValues> top(final int limit) {
                            return getStoredValueIterable(limit, false);
                        }

                        @Override
                        public Iterable<StoredValues> bottom(final int limit) {
                            return getStoredValueIterable(limit, true);
                        }

                        @Override
                        public long count() {
                            return data.countChildren(
                                    key,
                                    key.getChildDepth());
                        }

                        private StoredValues singleValue(final int trimmedSize, final boolean trimTop) {
                            final Iterable<StoredValues> values = getStoredValueIterable(trimmedSize, trimTop);
                            final Iterator<StoredValues> iterator = values.iterator();
                            if (iterator.hasNext()) {
                                return iterator.next();
                            }
                            return null;
                        }

                        private Iterable<StoredValues> getStoredValueIterable(final int limit,
                                                                              final boolean trimTop) {
                            final ItemsImpl items = data.getChildren(
                                    key,
                                    timeFilter,
                                    key.getChildDepth(),
                                    limit,
                                    trimTop);
                            if (items != null && items.size() > 0) {
                                return items.getStoredValueIterable();
                            }
                            return Collections::emptyIterator;
                        }
                    };
                };
                val = generator.eval(storedValues, childDataSupplier);
            } else {
                val = generator.eval(storedValues, null);
            }
            return val;
        }
    }

    private static class CompletionStateImpl implements CompletionState {

        private final LmdbDataStore lmdbDataStore;
        private final CountDownLatch complete;

        public CompletionStateImpl(final LmdbDataStore lmdbDataStore,
                                   final CountDownLatch complete) {
            this.lmdbDataStore = lmdbDataStore;
            this.complete = complete;
        }

        @Override
        public void signalComplete() {
            if (!isComplete()) {
                // Add an empty item to the transfer queue.
                lmdbDataStore.queue.complete();
            }
        }

        @Override
        public boolean isComplete() {
            boolean complete = true;

            try {
                complete = this.complete.await(0, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
            return complete;
        }

        @Override
        public void awaitCompletion() throws InterruptedException {
            complete.await();
        }

        @Override
        public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
            return complete.await(timeout, unit);
        }
    }

    private static class TransferState {

        private final AtomicBoolean terminated = new AtomicBoolean();
        private volatile Thread thread;

        public boolean isTerminated() {
            return terminated.get();
        }

        public synchronized void terminate() {
            terminated.set(true);
            if (thread != null) {
                thread.interrupt();
            }
        }

        public synchronized void setThread(final Thread thread) {
            this.thread = thread;
            if (terminated.get()) {
                if (thread != null) {
                    thread.interrupt();
                } else if (Thread.interrupted()) {
                    LOGGER.debug(() -> "Cleared interrupt state");
                }
            }
        }
    }
}
