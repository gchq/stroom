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
import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.dashboard.expression.v1.ValString;
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
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
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
import javax.validation.constraints.NotNull;

public class LmdbDataStore implements DataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStore.class);

    private static final long COMMIT_FREQUENCY_MS = 1000;

    private final LmdbEnv lmdbEnv;
    private final Dbi<ByteBuffer> dbi;

    private final FieldExpressionMatcher fieldExpressionMatcher;
    private final ExpressionOperator valueFilter;
    private final CompiledField[] compiledFields;
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
    private final KeyFactoryConfig keyFactoryConfig;
    private final KeyFactory keyFactory;
    private final LmdbPayloadCreator payloadCreator;
    private final TransferState transferState = new TransferState();

    private final Serialisers serialisers;

    private final WindowSupport windowSupport;


    private final int maxPutsBeforeCommit;
    private boolean storeLatestEventReference;
    private int streamIdFieldIndex = -1;
    private int eventIdFieldIndex = -1;

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
        compiledFields = CompiledFields.create(fields, fieldIndex, paramMap);
        compiledDepths = new CompiledDepths(compiledFields, modifiedTableSettings.showDetail());
        compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), compiledFields);
        keyFactoryConfig = new KeyFactoryConfigImpl(compiledFields, compiledDepths, dataStoreSettings);
        keyFactory = KeyFactoryFactory.create(serialisers, keyFactoryConfig, compiledDepths);
        lmdbRowKeyFactory = LmdbRowKeyFactoryFactory.create(keyFactory, keyFactoryConfig, compiledDepths);
        payloadCreator = new LmdbPayloadCreator(
                serialisers,
                queryKey,
                this,
                compiledFields,
                resultStoreConfig,
                lmdbRowKeyFactory,
                keyFactory);
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
    }

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    @Override
    public void add(final Val[] values) {
        // Filter incoming data.
        Map<String, Object> fieldIdToValueMap = null;
        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();
            if (expression != null) {
                final ValCache valCache = new ValCache(expression::createGenerator);
                if (valueFilter != null) {
                    if (fieldIdToValueMap == null) {
                        fieldIdToValueMap = new HashMap<>();
                    }
                    fieldIdToValueMap.put(compiledField.getField().getName(),
                            valCache.getVal(values).toString());
                }

                final CompiledFilter compiledFilter = compiledField.getCompiledFilter();
                if (compiledFilter != null) {
                    if (!compiledFilter.match(valCache.getVal(values).toString())) {
                        // We want to exclude this item so get out of this method ASAP.
                        return;
                    }
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
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        // Get a reference to the current event so we can keep track of what we have stored data for.
        final CurrentDbState currentDbState = createCurrentDbState(values);

        Key parentKey = Key.ROOT_KEY;
        long parentGroupHash = 0;

        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final Generator[] generators = new Generator[compiledFields.length];

            final int groupSize = groupSizeByDepth[depth];
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            final Val[] groupValues;
            if (groupSize > 0) {
                groupValues = new Val[groupSize];
            } else {
                groupValues = ValSerialiser.EMPTY_VALUES;
            }

            long timeMs = 0;

            int groupIndex = 0;
            for (int fieldIndex = 0; fieldIndex < compiledFields.length; fieldIndex++) {
                final CompiledField compiledField = compiledFields[fieldIndex];
                final Expression expression = compiledField.getExpression();
                final ValCache valCache = new ValCache(expression::createGenerator);
                // If we are grouping at this level then evaluate the expression and add to the group values.
                if (groupIndices[fieldIndex]) {
                    // If we haven't already created the generator then do so now.
                    final Val val = valCache.getVal(values);
                    groupValues[groupIndex++] = val;
                }

                // If we need a value at this level then evaluate the expression and add the value.
                if (valueIndices[fieldIndex]) {
                    final Generator generator = valCache.getGenerator();
                    if (iteration != -1) {
                        if (generator instanceof CountPrevious.Gen gen) {
                            if (gen.getIteration() == iteration) {
                                gen.set(values);
                            }
                        } else {
                            generator.set(values);
                        }
                    } else {
                        generator.set(values);
                    }
                    generators[fieldIndex] = generator;
                }

                // Get the value if this is a special field.
                if (timeMs == 0 && fieldIndex == keyFactoryConfig.getTimeFieldIndex()) {
                    final Val val = valCache.getVal(values);
                    if (val != null) {
                        timeMs = val.toLong();
                    }
                }
            }

            final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();
            final Key key;
            if (grouped) {
                // This is a grouped item.
                key = parentKey.resolve(timeMs, groupValues);

            } else {
                // This item will not be grouped.
                final long uniqueId = keyFactory.getUniqueId();
                key = parentKey.resolve(timeMs, uniqueId);
            }

            final long groupHash = ValHasher.hash(groupValues);
            final LmdbRowKey rowKey = lmdbRowKeyFactory.create(depth, parentGroupHash, groupHash, timeMs);
            final LmdbValue rowValue = new LmdbValue(
                    serialisers,
                    keyFactory,
                    key,
                    new Generators(serialisers, compiledFields, generators));

            // Force pack to create the byte buffer here prior to queueing.
            rowValue.getByteBuffer(errorConsumer);

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
                            if (uncommittedCount >= maxPutsBeforeCommit ||
                                    lastCommitMs < System.currentTimeMillis() - COMMIT_FREQUENCY_MS) {

                                // Commit
                                LOGGER.debug(() -> "Committing for elapsed time");
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

                final LmdbRowKey rowKey = lmdbKV.getRowKey();
                final LmdbValue rowValue = lmdbKV.getRowValue();

                // Just try to put first.
                final boolean success = put(
                        writeTxn,
                        dbi,
                        rowKey.getByteBuffer(),
                        rowValue.getByteBuffer(errorConsumer),
                        PutFlags.MDB_NOOVERWRITE);
                if (success) {
                    resultCount.incrementAndGet();

                } else if (lmdbRowKeyFactory.isGroup(rowKey)) {
                    // Get the existing entry for this key.
                    final ByteBuffer existingValueBuffer = dbi.get(writeTxn.getTxn(), rowKey.getByteBuffer());

                    final int minValSize = Math.max(1024, existingValueBuffer.remaining());
                    try (final UnsafeByteBufferOutput output =
                            serialisers.getOutputFactory().createByteBufferOutput(minValSize, errorConsumer)) {
                        boolean merged = false;

                        try (final UnsafeByteBufferInput input =
                                serialisers.getInputFactory().createByteBufferInput(existingValueBuffer)) {
                            while (!input.end()) {
                                final LmdbValue existingRowValue =
                                        LmdbValue.read(serialisers, keyFactory, compiledFields, input);

                                // If this is the same value then update it and reinsert.
                                if (Arrays.equals(
                                        existingRowValue.getFullKeyBytes(errorConsumer),
                                        rowValue.getFullKeyBytes(errorConsumer))) {
                                    final Generator[] generators = existingRowValue.getGenerators().getGenerators();
                                    final Generator[] newValue = rowValue.getGenerators().getGenerators();
                                    final Generator[] combined = combine(generators, newValue);

                                    LOGGER.trace(() -> "Merging combined value to output");
                                    final LmdbValue combinedValue = new LmdbValue(
                                            serialisers,
                                            keyFactory,
                                            existingRowValue.getKey(),
                                            new Generators(serialisers, compiledFields, combined));
                                    combinedValue.write(output, errorConsumer);

                                    // Copy any remaining values.
                                    if (!input.end()) {
                                        final byte[] remainingBytes = input.readAllBytes();
                                        output.writeBytes(remainingBytes, 0, remainingBytes.length);
                                    }

                                    merged = true;

                                } else {
                                    LOGGER.debug(() -> "Copying value to output");
                                    existingRowValue.write(output, errorConsumer);
                                }
                            }
                        }

                        // Append if we didn't merge.
                        if (!merged) {
                            LOGGER.debug(() -> "Appending value to output");
                            rowValue.write(output, errorConsumer);
                            resultCount.incrementAndGet();
                        }

                        output.flush();
                        final ByteBuffer newValue = output.getByteBuffer().flip();
                        final boolean ok = put(writeTxn, dbi, rowKey.getByteBuffer(), newValue);
                        if (!ok) {
                            LOGGER.debug(() -> "Unable to update");
                            throw new RuntimeException("Unable to update");
                        }
                    }

                } else {
                    // We do not expect a key collision here.
                    LOGGER.debug(() -> "Unexpected collision");
                    throw new RuntimeException("Unexpected collision");
                }

            } catch (final RuntimeException | IOException e) {
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

    private Generator[] combine(final Generator[] existing, final Generator[] value) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_COMBINE);
        return Metrics.measure("Combine", () -> {
            // Combine the new item into the original item.
            for (int i = 0; i < existing.length; i++) {
                Generator existingGenerator = existing[i];
                Generator newGenerator = value[i];
                if (newGenerator != null) {
                    if (existingGenerator == null) {
                        existing[i] = newGenerator;
                    } else {
                        existingGenerator.merge(newGenerator);
                    }
                }
            }

            return existing;
        });
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
                                    keyFactory,
                                    serialisers,
                                    dbi,
                                    readTxn,
                                    compiledFields,
                                    compiledSorters,
                                    maxResults,
                                    queryKey))));
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

    private static class ValCache {

        private final Provider<Generator> generatorProvider;
        private Generator generator;
        private Val val;

        public ValCache(final Provider<Generator> generatorProvider) {
            this.generatorProvider = generatorProvider;
        }

        Generator getGenerator() {
            if (generator == null) {
                generator = generatorProvider.get();
            }
            return generator;
        }

        Val getVal(final Val[] values) {
            if (val == null) {
                final Generator generator = getGenerator();
                generator.set(values);

                // If we are filtering then we need to evaluate this field
                // now so that we can filter the resultant value.
                val = generator.eval(null);
            }
            return val;
        }
    }

    private static class LmdbData implements Data {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbData.class);

        private final LmdbRowKeyFactory lmdbRowKeyFactory;
        private final KeyFactory keyFactory;
        private final Serialisers serialisers;
        private final Dbi<ByteBuffer> dbi;
        private final Txn<ByteBuffer> readTxn;
        private final CompiledField[] compiledFields;
        private final CompiledSorter<Item>[] compiledSorters;
        private final Sizes maxResults;
        private final QueryKey queryKey;

        public LmdbData(final LmdbRowKeyFactory lmdbRowKeyFactory,
                        final KeyFactory keyFactory,
                        final Serialisers serialisers,
                        final Dbi<ByteBuffer> dbi,
                        final Txn<ByteBuffer> readTxn,
                        final CompiledField[] compiledFields,
                        final CompiledSorter<Item>[] compiledSorters,
                        final Sizes maxResults,
                        final QueryKey queryKey) {
            this.lmdbRowKeyFactory = lmdbRowKeyFactory;
            this.keyFactory = keyFactory;
            this.serialisers = serialisers;
            this.dbi = dbi;
            this.readTxn = readTxn;
            this.compiledFields = compiledFields;
            this.compiledSorters = compiledSorters;
            this.maxResults = maxResults;
            this.queryKey = queryKey;
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

//                final byte[] start = ByteBufferUtils.toBytes(keyRange.getStart());
//                final byte[] stop = ByteBufferUtils.toBytes(keyRange.getStop());
//
//                NestedGroupedLmdbRowKeyFactory fac = (NestedGroupedLmdbRowKeyFactory) lmdbRowKeyFactory;
//                ByteBuffer parentKeyByteBuffer = fac.createKey(parentKey);
//                final byte[] parentKeyByteBufferBytes = ByteBufferUtils.toBytes(parentKeyByteBuffer);

                try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(
                        readTxn,
                        keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();

                    while (iterator.hasNext()
                            && addMore
                            && !Thread.currentThread().isInterrupted()) {

                        final KeyVal<ByteBuffer> keyVal = iterator.next();
//                        final byte[] currentKeyBytes = ByteBufferUtils.toBytes(keyVal.key());

                        // All valid keys are more than a single byte long. Single byte keys are used to store db info.
                        if (keyVal.key().limit() > 1) {
                            final ByteBuffer valueBuffer = keyVal.val();
                            try (final UnsafeByteBufferInput input =
                                    serialisers.getInputFactory().createByteBufferInput(valueBuffer)) {
                                while (!input.end() && addMore) {
                                    final LmdbValue rowValue = LmdbValue.read(serialisers,
                                            keyFactory,
                                            compiledFields,
                                            input);
                                    final Key key = rowValue.getKey();
//                            if (key.getParent().equals(parentKey)) {
                                    final Generator[] generators = rowValue.getGenerators().getGenerators();
                                    list.add(new ItemImpl(this, key, timeFilter, generators));
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
        public Item get(final int index) {
            return array[index];
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        @NotNull
        public Iterator<Item> iterator() {
            return new Iterator<>() {
                private int pos = 0;

                @Override
                public boolean hasNext() {
                    return size > pos;
                }

                @Override
                public Item next() {
                    return array[pos++];
                }
            };
        }
    }

    public static class ItemImpl implements Item {

        private final LmdbData data;
        private final Key key;
        private final TimeFilter timeFilter;
        private final Generator[] generators;

        public ItemImpl(final LmdbData data,
                        final Key key,
                        final TimeFilter timeFilter,
                        final Generator[] generators) {
            this.data = data;
            this.key = key;
            this.timeFilter = timeFilter;
            this.generators = generators;
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index, final boolean evaluateChildren) {
            Val val = null;

            final Generator generator = generators[index];
            if (generator != null) {
                if (key.isGrouped()) {
                    final Supplier<ChildData> childDataSupplier = () -> {
                        // If we don't have any children at the requested depth then return null.
                        if (data.compiledSorters.length <= key.getChildDepth()) {
                            return null;
                        }

                        return new ChildData() {
                            @Override
                            public Val first() {
                                return singleValue(1, false);
                            }

                            @Override
                            public Val last() {
                                return singleValue(1, true);
                            }

                            @Override
                            public Val nth(final int pos) {
                                return singleValue(pos + 1, false);
                            }

                            @Override
                            public Val top(final String delimiter, final int limit) {
                                return join(delimiter, limit, false);
                            }

                            @Override
                            public Val bottom(final String delimiter, final int limit) {
                                return join(delimiter, limit, true);
                            }

                            @Override
                            public Val count() {
                                final long count = data.countChildren(
                                        key,
                                        key.getChildDepth());
                                if (count <= 0) {
                                    return ValNull.INSTANCE;
                                }
                                return ValLong.create(count);
                            }

                            private Val singleValue(final int trimmedSize, final boolean trimTop) {
                                final Items items = data.getChildren(
                                        key,
                                        timeFilter,
                                        key.getChildDepth(),
                                        trimmedSize,
                                        trimTop);
                                if (items != null && items.size() == trimmedSize) {
                                    return items.get(trimmedSize - 1).getValue(index, true);
                                }
                                return ValNull.INSTANCE;
                            }

                            private Val join(final String delimiter, final int limit, final boolean trimTop) {
                                final Items items = data.getChildren(
                                        key,
                                        timeFilter,
                                        key.getChildDepth(),
                                        limit,
                                        trimTop);
                                if (items != null && items.size() > 0) {
                                    final StringBuilder sb = new StringBuilder();
                                    for (final Item item : items) {
                                        final Val val = item.getValue(index, true);
                                        if (val.type().isValue()) {
                                            if (sb.length() > 0) {
                                                sb.append(delimiter);
                                            }
                                            sb.append(val);
                                        }
                                    }
                                    return ValString.create(sb.toString());
                                }
                                return ValNull.INSTANCE;
                            }
                        };
                    };
                    val = generator.eval(childDataSupplier);

                } else {
                    val = generator.eval(null);
                }
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
