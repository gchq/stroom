/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.expression.api.DateTimeSettings;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.ReadTxn;
import stroom.lmdb2.WriteTxn;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.ChildData;
import stroom.query.language.functions.CountPrevious;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.time.SimpleDuration;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jakarta.inject.Provider;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Env.MapFullException;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.LmdbException;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LmdbDataStore implements DataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStore.class);

    private static final long COMMIT_FREQUENCY_MS = 10000;
    public static final ByteBuffer DB_STATE_VALUE = ByteBuffer
            .allocateDirect(Long.BYTES + Long.BYTES + Long.BYTES);

    private final LmdbEnv env;
    private final LmdbDb db;
    private final ColumnExpressionMatcher columnExpressionMatcher;
    private final ExpressionOperator valueFilter;
    private final ValueReferenceIndex valueReferenceIndex;
    private final List<Column> columns;
    private final CompiledColumns compiledColumns;
    private final CompiledColumn[] compiledColumnArray;
    private final CompiledSorter<Item>[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final LmdbPutFilter putFilter;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private final LmdbWriteQueue queue;
    private final CountDownLatch complete = new CountDownLatch(1);
    private final CompletionState completionState = new CompletionStateImpl(this, complete);
    private final QueryKey queryKey;
    private final String componentId;
    private final FieldIndex fieldIndex;
    private final boolean producePayloads;
    private final Sizes maxResultSizes;
    private final ErrorConsumer errorConsumer;
    private final DataWriterFactory writerFactory;
    private final LmdbRowKeyFactory lmdbRowKeyFactory;
    private final LmdbRowValueFactory lmdbRowValueFactory;
    private final KeyFactoryConfig keyFactoryConfig;
    private final KeyFactory keyFactory;
    private final LmdbPayloadCreator payloadCreator;
    private final TransferState transferState = new TransferState();

    private final WindowSupport windowSupport;


    private final int maxPutsBeforeCommit;
    private final CurrentDbStateFactory currentDbStateFactory;

    private final StoredValueKeyFactory storedValueKeyFactory;
    private final int maxSortedItems;
    private final DateTimeSettings dateTimeSettings;
    private final ByteBufferFactory bufferFactory;

    public LmdbDataStore(final SearchRequestSource searchRequestSource,
                         final LmdbEnv.Builder lmdbEnvBuilder,
                         final AbstractResultStoreConfig resultStoreConfig,
                         final QueryKey queryKey,
                         final String componentId,
                         final TableSettings tableSettings,
                         final ExpressionContext expressionContext,
                         final FieldIndex fieldIndex,
                         final Map<CIKey, String> paramMap,
                         final DataStoreSettings dataStoreSettings,
                         final Provider<Executor> executorProvider,
                         final ErrorConsumer errorConsumer,
                         final ByteBufferFactory bufferFactory) {
        this.bufferFactory = bufferFactory;
        this.queryKey = queryKey;
        this.componentId = componentId;
        this.fieldIndex = fieldIndex;
        this.producePayloads = dataStoreSettings.isProducePayloads();
        this.maxResultSizes = dataStoreSettings.getMaxResults() == null
                ? Sizes.unlimited()
                : dataStoreSettings.getMaxResults();
        this.errorConsumer = errorConsumer;

        // Ensure we have a source type.
        final SourceType sourceType =
                Optional.ofNullable(searchRequestSource)
                        .map(SearchRequestSource::getSourceType)
                        .orElse(SourceType.DASHBOARD_UI);

        this.windowSupport = new WindowSupport(tableSettings);
        final TableSettings modifiedTableSettings = windowSupport.getTableSettings();
        columns = Objects.requireNonNullElse(modifiedTableSettings.getColumns(), Collections.emptyList());
        queue = new LmdbWriteQueue(resultStoreConfig.getValueQueueSize(), bufferFactory);
        maxSortedItems = resultStoreConfig.getMaxSortedItems();
        valueFilter = modifiedTableSettings.getValueFilter();
        this.dateTimeSettings = expressionContext == null
                ? null
                : expressionContext.getDateTimeSettings();
        columnExpressionMatcher = new ColumnExpressionMatcher(columns, dateTimeSettings);
        this.compiledColumns = CompiledColumns.create(expressionContext, columns, fieldIndex, paramMap);
        this.compiledColumnArray = compiledColumns.getCompiledColumns();
        valueReferenceIndex = compiledColumns.getValueReferenceIndex();
        compiledDepths = new CompiledDepths(this.compiledColumnArray, modifiedTableSettings.showDetail());
        compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), this.compiledColumnArray);
        writerFactory = new DataWriterFactory(
                errorConsumer,
                resultStoreConfig.getMaxStringFieldLength());
        keyFactoryConfig = new KeyFactoryConfigImpl(sourceType, this.compiledColumnArray, compiledDepths);
        keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        final ValHasher valHasher = new ValHasher(writerFactory);
        storedValueKeyFactory = new StoredValueKeyFactoryImpl(
                compiledDepths,
                compiledColumnArray,
                keyFactoryConfig,
                valHasher);
        lmdbRowKeyFactory = LmdbRowKeyFactoryFactory
                .create(bufferFactory, keyFactory, keyFactoryConfig, compiledDepths, storedValueKeyFactory);
        lmdbRowValueFactory = new LmdbRowValueFactory(
                bufferFactory,
                valueReferenceIndex,
                writerFactory);
        payloadCreator = new LmdbPayloadCreator(
                queryKey,
                this,
                resultStoreConfig,
                lmdbRowKeyFactory,
                bufferFactory);
        maxPutsBeforeCommit = resultStoreConfig.getMaxPutsBeforeCommit();

        this.env = lmdbEnvBuilder
                .maxDbs(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .maxReaders(1)
                .errorHandler(this::error)
                .build();
        this.db = env.openDb(queryKey + "_" + componentId);

        // Filter puts to the store if we need to. This filter has the effect of preventing addition of items if we have
        // reached the max result size if specified and aren't grouping or sorting.
        putFilter = LmdbPutFilterFactory.create(
                compiledSorters,
                compiledDepths,
                maxResultSizes,
                totalResultCount,
                completionState);

        // Create a factory that makes DB state objects.
        currentDbStateFactory = new CurrentDbStateFactory(sourceType, fieldIndex, dataStoreSettings);

        // Start transfer loop.
        executorProvider.get().execute(this::transfer);
    }

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    @Override
    public void accept(final Val[] values) {
        // Filter incoming data.
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        Map<CIKey, Object> fieldIdToValueMap = null;
        for (final CompiledColumn compiledColumn : compiledColumnArray) {
            final Generator generator = compiledColumn.getGenerator();
            if (generator != null) {
                final CompiledFilter compiledFilter = compiledColumn.getCompiledFilter();
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
                    final CIKey caseInsensitiveFieldName = CIKey.of(compiledColumn.getColumn().getName());
                    fieldIdToValueMap.put(caseInsensitiveFieldName, string);
                }
            }
        }

        if (fieldIdToValueMap != null) {
            try {
                // If the value filter doesn't match then get out of here now.
                if (!columnExpressionMatcher.match(fieldIdToValueMap, valueFilter)) {
                    return;
                }
            } catch (final RuntimeException e) {
                error(e);
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
        final CurrentDbState currentDbState = currentDbStateFactory.createCurrentDbState(values);

        ByteBuffer parentRowKey = null;
        final LmdbKV[] rows = new LmdbKV[groupIndicesByDepth.length];
        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            for (int columnIndex = 0; columnIndex < compiledColumnArray.length; columnIndex++) {
                final CompiledColumn compiledColumn = compiledColumnArray[columnIndex];
                final Generator generator = compiledColumn.getGenerator();

                // If we need a value at this level then set the raw values.
                if (valueIndices[columnIndex] ||
                        columnIndex == keyFactoryConfig.getTimeColumnIndex()) {
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

            final ByteBuffer rowKey = lmdbRowKeyFactory.create(depth, parentRowKey, storedValues);
            final ByteBuffer rowValue = lmdbRowValueFactory.create(storedValues);
            parentRowKey = rowKey;
            rows[depth] = new LmdbKV(currentDbState, rowKey, rowValue);
        }

        // We build rows first before putting to ensure that the byte buffers used for the parent row key are
        // not released and reused before we have read the values from them.
        for (final LmdbKV row : rows) {
            put(row);
        }
    }

    public void putCurrentDbState(final long streamId,
                                  final Long eventId,
                                  final Long lastEventTime) {
        final CurrentDbState currentDbState = new CurrentDbState(streamId, eventId, lastEventTime);
        put(new CurrentDbStateLmdbQueueItem(currentDbState));
    }

    void put(final LmdbQueueItem queueItem) {
        LOGGER.trace(() -> "put");
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_PUT);

        // Some searches can be terminated early if the user is not sorting or grouping.
        putFilter.put(queueItem, this::doPut);
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
            try {
                env.write(writeTxn -> {
                    CurrentDbState currentDbState = getCurrentDbState();
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
                                    insert(writeTxn, db, lmdbKV);
                                    uncommittedCount++;
                                } else if (queueItem instanceof
                                        final CurrentDbStateLmdbQueueItem currentDbStateLmdbQueueItem) {
                                    currentDbState = currentDbStateLmdbQueueItem.getCurrentDbState()
                                            .mergeExisting(currentDbState);
                                } else if (queueItem instanceof final Sync sync) {
                                    commit(writeTxn, currentDbState);
                                    sync.sync();
                                } else if (queueItem instanceof final DeleteCommand deleteCommand) {
                                    delete(writeTxn, deleteCommand);
                                }
                            }

                            if (producePayloads && payloadCreator.isEmpty()) {
                                // Commit
                                LOGGER.debug(() -> "Committing for new payload");

                                commit(writeTxn, currentDbState);
                                lastCommitMs = System.currentTimeMillis();
                                uncommittedCount = 0;

                                // Create payload and clear the DB.
                                payloadCreator.addPayload(writeTxn, db, false);

                            } else if (uncommittedCount > 0) {
                                final long count = uncommittedCount;
                                if (count >= maxPutsBeforeCommit ||
                                        lastCommitMs < System.currentTimeMillis() - COMMIT_FREQUENCY_MS) {

                                    // Commit
                                    LOGGER.trace(() -> {
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
                        error(e);
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
                            finalPayload = payloadCreator.addPayload(writeTxn, db, true);
                        }
                        // Make sure we end with an empty payload to indicate completion.
                        // Adding a final empty payload to the queue ensures that a consuming node will have to request
                        // the payload from the queue before we complete.
                        LOGGER.debug(() -> "Final payload");
                        payloadCreator.finalPayload();
                    }
                });
            } catch (final Throwable e) {
                LOGGER.error(e::getMessage, e);
                error(e);
            } finally {
                // Ensure we complete.
                queue.terminate();
                // The LMDB environment will be closed after we complete.
                complete.countDown();
                LOGGER.debug(() -> "Finished transfer while loop");
                transferState.setThread(null);
            }
        });
    }

    private void delete(final WriteTxn writeTxn,
                        final DeleteCommand deleteCommand) {
        lmdbRowKeyFactory.createChildKeyRange(
                deleteCommand.getParentKey(), deleteCommand.getTimeFilter(), keyRange -> {
                    db.iterate(writeTxn, keyRange, iterator -> {
                        while (iterator.hasNext()) {
                            final KeyVal<ByteBuffer> keyVal = iterator.next();
                            final ByteBuffer keyBuffer = keyVal.key();
                            if (keyBuffer.limit() > 1) {
                                db.delete(writeTxn, keyBuffer);
                            }
                        }
                    });
                    writeTxn.commit();
                });
    }

    private void commit(final WriteTxn writeTxn,
                        final CurrentDbState currentDbState) {
        putCurrentDbState(writeTxn, currentDbState);
        writeTxn.commit();
    }


    private void insert(final WriteTxn writeTxn,
                        final LmdbDb db,
                        final LmdbKV lmdbKV) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_INSERT);
        Metrics.measure("Insert", () -> {
            try {
                LOGGER.trace(() -> "insert");

                // Just try to put first.
                final boolean success = put(
                        writeTxn,
                        db,
                        lmdbKV.getRowKey(),
                        lmdbKV.getRowValue(),
                        PutFlags.MDB_NOOVERWRITE);
                if (success) {
                    resultCount.incrementAndGet();

                } else {
                    final int depth = lmdbRowKeyFactory.getDepth(lmdbKV);
                    if (lmdbRowKeyFactory.isGroup(depth)) {
                        final StoredValues newStoredValues = readValues(lmdbKV.getRowValue().duplicate());
                        final Val[] newGroupValues
                                = storedValueKeyFactory.getGroupValues(depth, newStoredValues);

                        // Get the existing entry for this key.
                        final ByteBuffer existingValueBuffer = db.get(writeTxn, lmdbKV.getRowKey());
                        final ByteBuffer newValueBuffer = lmdbRowValueFactory.useOutput(output -> {
                            boolean merged = false;
                            while (existingValueBuffer.remaining() > 0) {
                                final int startPos = existingValueBuffer.position();
                                final StoredValues existingStoredValues = readValues(existingValueBuffer);
                                final int endPos = existingValueBuffer.position();
                                final Val[] existingGroupValues
                                        = storedValueKeyFactory.getGroupValues(depth, existingStoredValues);

                                // If this is the same value then update it and reinsert.
                                if (Arrays.equals(existingGroupValues, newGroupValues)) {
                                    for (final CompiledColumn compiledColumn : compiledColumnArray) {
                                        compiledColumn.getGenerator().merge(existingStoredValues, newStoredValues);
                                    }

                                    LOGGER.trace(() -> "Merging combined value to output");
                                    try (final DataWriter writer = writerFactory.create(output)) {
                                        valueReferenceIndex.write(existingStoredValues, writer);
                                    }

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

                        final boolean ok = put(writeTxn, db, lmdbKV.getRowKey(), newValueBuffer);
                        bufferFactory.release(newValueBuffer);

                        if (!ok) {
                            LOGGER.debug(() -> "Unable to update");
                            throw new RuntimeException("Unable to update");
                        }

                    } else {
                        // We do not expect a key collision here.
                        final String message = "Unexpected collision (" +
                                "key factory=" +
                                keyFactory.getClass().getSimpleName() +
                                ", " +
                                "lmdbRowKeyFactory=" +
                                lmdbRowKeyFactory.getClass().getSimpleName() +
                                ")";
                        LOGGER.debug(message);
                        throw new RuntimeException(message);
                    }
                }

            } catch (final RuntimeException e) {
                error(e);
                if (LOGGER.isTraceEnabled()) {
                    // Only evaluate queue item value in trace as it can be expensive.
                    LOGGER.trace(() -> "Error putting " + lmdbKV + " (" + e.getMessage() + ")", e);
                } else {
                    LOGGER.debug(() -> "Error putting queueItem (" + e.getMessage() + ")", e);
                }

                error(new RuntimeException("Error adding data into search result store", e));
                throw e;

            } finally {
                // Release buffers back to the pool.
                bufferFactory.release(lmdbKV.getRowKey());
                bufferFactory.release(lmdbKV.getRowValue());
            }
        });
    }

    private boolean put(final WriteTxn writeTxn,
                        final LmdbDb db,
                        final ByteBuffer key,
                        final ByteBuffer val,
                        final PutFlags... flags) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_DBI_PUT);
        return db.put(writeTxn, key, val, flags);
    }

    private void error(final Throwable e) {
        LOGGER.debug(e::getMessage, e);
        if (e instanceof MapFullException) {
            errorConsumer.add(() -> "Unable to add search result as result store has reached max capacity of " +
                    env.getMaxStoreSize());
        } else if (e instanceof LmdbException) {
            String message = e.getMessage();
            if (message != null) {
                // Remove native LMDB error code as it means nothing to users.
                if (message.endsWith(")")) {
                    final int index = message.lastIndexOf(" (");
                    if (index != -1) {
                        message = message.substring(0, index);
                    }
                }
                final String msg = message;
                errorConsumer.add(() -> msg);
            }
        } else {
            errorConsumer.add(e);
        }
    }

    @Override
    public List<Column> getColumns() {
        return compiledColumns.getColumns();
    }

    public synchronized void close() {
        LOGGER.debug(() -> "close called");
        LOGGER.trace(() -> "close()", new RuntimeException("close"));
        if (shutdown.compareAndSet(false, true)) {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_CLEAR);

            // Let the transfer loop know it should stop ASAP.
            transferState.terminate();

            // Terminate the queue.
            queue.terminate();

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

            env.close();
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
            env.delete();
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
        return FileUtil.getByteSize(env.getDir().getEnvDir());
    }

    @Override
    public KeyFactory getKeyFactory() {
        return keyFactory;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    private void putCurrentDbState(final WriteTxn writeTxn, final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            final ByteBuffer keyBuffer = LmdbRowKeyFactoryFactory.DB_STATE_KEY;
            ByteBuffer valueBuffer = DB_STATE_VALUE;
            valueBuffer.clear();
            putLong(valueBuffer, currentDbState.getStreamId());
            putLong(valueBuffer, currentDbState.getEventId());
            putLong(valueBuffer, currentDbState.getLastEventTime());
            valueBuffer = valueBuffer.flip();

            final boolean success = put(
                    writeTxn,
                    db,
                    keyBuffer,
                    valueBuffer);
            if (!success) {
                LOGGER.error("Unable to store event ref");
            }
        }
    }

    private synchronized CurrentDbState getCurrentDbState() {
        if (!currentDbStateFactory.isStoreLatestEventReference()) {
            return null;
        }

        final AtomicReference<CurrentDbState> currentDbStateAtomicReference = new AtomicReference<>();
        final KeyRange<ByteBuffer> keyRange = LmdbRowKeyFactoryFactory.DB_STATE_KEY_RANGE;
        env.read(readTxn -> db.iterate(readTxn, keyRange, iterator -> {
            if (iterator.hasNext()) {
                final KeyVal<ByteBuffer> keyVal = iterator.next();
                final ByteBuffer key = keyVal.key();
                if (LmdbRowKeyFactoryFactory.isStateKey(key)) {
                    final ByteBuffer val = keyVal.val();
                    final long streamId = Objects.requireNonNull(getLong(val, 0),
                            "streamId not present in DB state");
                    final Long eventId = getLong(val, Long.BYTES);
                    final Long lastEventTime = getLong(val, Long.BYTES + Long.BYTES);
                    currentDbStateAtomicReference.set(new CurrentDbState(streamId, eventId, lastEventTime));
                }
            }
        }));

        return currentDbStateAtomicReference.get();
    }

    private Long getLong(final ByteBuffer valueBuffer, final int index) {
        final long l = valueBuffer.getLong(index);
        if (l == -1) {
            return null;
        }
        return l;
    }

    private void putLong(final ByteBuffer valueBuffer, final Long l) {
        valueBuffer.putLong(GwtNullSafe.requireNonNullElse(l, -1L));
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
            LOGGER.debug(() -> "Current Db State: " + currentDbState);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return currentDbState;
    }

    public void delete(final DeleteCommand deleteCommand) {
        put(deleteCommand);
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    @Override
    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    @Override
    public synchronized <R> void fetch(final OffsetRange range,
                                       final OpenGroups openGroups,
                                       final TimeFilter timeFilter,
                                       final ItemMapper<R> mapper,
                                       final Consumer<R> resultConsumer,
                                       final Consumer<Long> totalRowCountConsumer) {
        final OffsetRange enforcedRange = Optional
                .ofNullable(range)
                .orElse(OffsetRange.ZERO_100);

        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET);

        if (env.isClosed()) {
            // If we query LMDB after the env has been closed then we are likely to crash the JVM
            // see https://github.com/lmdbjava/lmdbjava/issues/185
            LOGGER.debug(() -> "fetch called (queryKey =" +
                    queryKey +
                    ", componentId=" +
                    componentId +
                    ") after store has been shut down");

        } else {
            env.read(readTxn ->
                    Metrics.measure("fetch", () -> {
                        try {
                            final FetchState fetchState = new FetchState();
                            fetchState.countRows = totalRowCountConsumer != null;
                            fetchState.reachedRowLimit = fetchState.length >= enforcedRange.getLength();
                            fetchState.keepGoing = fetchState.justCount || !fetchState.reachedRowLimit;

                            final LmdbReadContext readContext =
                                    new LmdbReadContext(LmdbDataStore.this, db, readTxn, timeFilter);

                            getChildren(
                                    readContext,
                                    Key.ROOT_KEY,
                                    0,
                                    maxResultSizes.size(0),
                                    false,
                                    openGroups,
                                    timeFilter,
                                    mapper,
                                    enforcedRange,
                                    fetchState,
                                    resultConsumer);

                            if (totalRowCountConsumer != null) {
                                totalRowCountConsumer.accept(fetchState.totalRowCount);
                            }
                        } catch (final Throwable e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    }));
        }
    }

    private <R> void getChildren(final LmdbReadContext readContext,
                                 final Key parentKey,
                                 final int depth,
                                 long limit,
                                 final boolean trimTop,
                                 final OpenGroups openGroups,
                                 final TimeFilter timeFilter,
                                 final ItemMapper<R> mapper,
                                 final OffsetRange range,
                                 final FetchState fetchState,
                                 final Consumer<R> resultConsumer) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET_CHILDREN);

            // If we aren't sorting then just return results directly.
            if (fetchState.justCount || compiledSorters[depth] == null) {
                // Get children without sorting.
                getUnsortedChildren(
                        readContext,
                        parentKey,
                        depth,
                        limit,
                        openGroups,
                        timeFilter,
                        mapper,
                        range,
                        fetchState,
                        resultConsumer);
            } else {
                // Get sorted children.
                getSortedChildren(
                        readContext,
                        parentKey,
                        depth,
                        limit,
                        trimTop,
                        openGroups,
                        timeFilter,
                        mapper,
                        range,
                        fetchState,
                        resultConsumer);
            }
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }


    private <R> void getUnsortedChildren(final LmdbReadContext readContext,
                                         final Key parentKey,
                                         final int depth,
                                         final long limit,
                                         final OpenGroups openGroups,
                                         final TimeFilter timeFilter,
                                         final ItemMapper<R> mapper,
                                         final OffsetRange range,
                                         final FetchState fetchState,
                                         final Consumer<R> resultConsumer) {
        // Reduce the number of groups left to go into.
        openGroups.complete(parentKey);

        lmdbRowKeyFactory.createChildKeyRange(parentKey, timeFilter, keyRange ->
                readContext.read(keyRange, iterator -> {
                    long childCount = 0;

                    while (fetchState.keepGoing &&
                            childCount < limit &&
                            iterator.hasNext() &&
                            !Thread.currentThread().isInterrupted()) {

                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        // All valid keys are more than a single byte long. Single byte keys are used to store db info.
                        if (LmdbRowKeyFactoryFactory.isNotStateKey(keyVal.key())) {
                            final ByteBuffer keyBuffer = keyVal.key();
                            final ByteBuffer valueBuffer = keyVal.val();

                            // If we are just counting the total results from this point then we don't need to
                            // deserialise unless we are hiding rows.
                            if (fetchState.justCount) {
                                if (mapper.hidesRows()) {
                                    final StoredValues storedValues = readValues(valueBuffer);
                                    final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                    final ItemImpl item = new ItemImpl(
                                            readContext,
                                            key,
                                            storedValues);
                                    final R row = mapper.create(columns, item);
                                    if (row != null) {
                                        childCount++;
                                        fetchState.totalRowCount++;

                                        // Add children if the group is open.
                                        final int childDepth = depth + 1;
                                        if (openGroups.isOpen(item.getKey()) &&
                                                compiledSorters.length > childDepth) {
                                            getUnsortedChildren(
                                                    readContext,
                                                    key,
                                                    childDepth,
                                                    maxResultSizes.size(childDepth),
                                                    openGroups,
                                                    timeFilter,
                                                    mapper,
                                                    range,
                                                    fetchState,
                                                    resultConsumer);
                                        }
                                    }
                                } else {
                                    childCount++;
                                    fetchState.totalRowCount++;

                                    // Only bother to test open groups if we have some left.
                                    if (openGroups.isNotEmpty()) {
                                        // Add children if the group is open.
                                        final StoredValues storedValues = readValues(valueBuffer);
                                        final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                        final int childDepth = depth + 1;
                                        if (openGroups.isOpen(key) &&
                                                compiledSorters.length > childDepth) {
                                            getUnsortedChildren(
                                                    readContext,
                                                    key,
                                                    childDepth,
                                                    maxResultSizes.size(childDepth),
                                                    openGroups,
                                                    timeFilter,
                                                    mapper,
                                                    range,
                                                    fetchState,
                                                    resultConsumer);
                                        }
                                    }
                                }

                            } else {
                                do {
                                    final StoredValues storedValues = readValues(valueBuffer);
                                    final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                    final ItemImpl item = new ItemImpl(
                                            readContext,
                                            key,
                                            storedValues);
                                    final R row = mapper.create(columns, item);
                                    if (row != null) {
                                        childCount++;
                                        fetchState.totalRowCount++;

                                        if (!fetchState.reachedRowLimit) {
                                            if (range.getOffset() <= fetchState.offset) {
                                                resultConsumer.accept(row);
                                                fetchState.length++;
                                                fetchState.reachedRowLimit = fetchState.length >= range.getLength();
                                                if (fetchState.reachedRowLimit) {
                                                    if (fetchState.countRows) {
                                                        fetchState.justCount = true;
                                                    } else {
                                                        fetchState.keepGoing = false;
                                                    }
                                                }
                                            }
                                            fetchState.offset++;
                                        }

                                        // Add children if the group is open.
                                        final int childDepth = depth + 1;
                                        if (fetchState.keepGoing &&
                                                openGroups.isOpen(key) &&
                                                compiledSorters.length > childDepth) {
                                            getUnsortedChildren(
                                                    readContext,
                                                    key,
                                                    childDepth,
                                                    maxResultSizes.size(childDepth),
                                                    openGroups,
                                                    timeFilter,
                                                    mapper,
                                                    range,
                                                    fetchState,
                                                    resultConsumer);
                                        }
                                    }
                                } while (fetchState.keepGoing && valueBuffer.hasRemaining());
                            }
                        }
                    }
                }));
    }

    private <R> void getSortedChildren(final LmdbReadContext readContext,
                                       final Key parentKey,
                                       final int depth,
                                       final long limit,
                                       final boolean trimTop,
                                       final OpenGroups openGroups,
                                       final TimeFilter timeFilter,
                                       final ItemMapper<R> mapper,
                                       final OffsetRange range,
                                       final FetchState fetchState,
                                       final Consumer<R> resultConsumer) {
        // Remember that we have gone into this group, so we don't keep trying.
        openGroups.complete(parentKey);

        final CompiledSorter<Item> sorter = compiledSorters[depth];

        lmdbRowKeyFactory.createChildKeyRange(parentKey, timeFilter, keyRange -> {
            final long lengthRemaining = range.getOffset() + range.getLength() - fetchState.length;
            final int trimmedSize = (int) Math.max(Math.min(Math.min(limit, lengthRemaining), maxSortedItems), 0);

            int maxSize = trimmedSize * 2;
            maxSize = Math.max(maxSize, 1_000);

            final SortedItems sortedItems = new SortedItems(10, maxSize, trimmedSize, trimTop, sorter);
            readContext.read(keyRange, iterator -> {
                long totalRowCount = 0;
                while (iterator.hasNext()
                        && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> keyVal = iterator.next();

                    // All valid keys are more than a single byte long. Single byte keys are used to store db
                    // info.
                    if (LmdbRowKeyFactoryFactory.isNotStateKey(keyVal.key())) {
                        final ByteBuffer keyBuffer = keyVal.key();
                        final ByteBuffer valueBuffer = keyVal.val();
                        boolean isFirstValue = true;
                        // It is possible to have no actual values, e.g. if you have just one col of
                        // 'currentUser()' so we still need to create and add an empty storedValues
                        while (valueBuffer.hasRemaining() || isFirstValue) {
                            isFirstValue = false;

                            final StoredValues storedValues = readValues(valueBuffer);
                            final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                            final ItemImpl item = new ItemImpl(readContext, key, storedValues);
                            if (mapper.hidesRows()) {
                                final R row = mapper.create(columns, item);
                                if (row != null) {
                                    totalRowCount++;
                                    sortedItems.add(new ItemImpl(readContext, key, storedValues));
                                }
                            } else {
                                totalRowCount++;
                                sortedItems.add(new ItemImpl(readContext, key, storedValues));
                            }
                        }
                    }
                }

                // If there is a limit then pretend that the total row count is constrained.
                fetchState.totalRowCount += Math.min(totalRowCount, limit);
            });

            // Finally transfer the sorted items to the result.
            long childCount = 0;
            for (final Item item : sortedItems.getIterable()) {
                if (childCount >= limit) {
                    break;
                }
                childCount++;

                if (!fetchState.reachedRowLimit) {
                    if (range.getOffset() <= fetchState.offset) {
                        final R row = mapper.create(columns, item);
                        resultConsumer.accept(row);
                        fetchState.length++;
                        fetchState.reachedRowLimit = fetchState.length >= range.getLength();
                        if (fetchState.reachedRowLimit) {
                            if (fetchState.countRows) {
                                fetchState.justCount = true;
                            } else {
                                fetchState.keepGoing = false;
                            }
                        }
                    }
                    fetchState.offset++;
                }

                // Add children if the group is open.
                final int childDepth = depth + 1;
                if (fetchState.keepGoing &&
                        compiledSorters.length > childDepth &&
                        openGroups.isOpen(item.getKey())) {
                    getChildren(readContext,
                            item.getKey(),
                            childDepth,
                            maxResultSizes.size(childDepth),
                            trimTop,
                            openGroups,
                            timeFilter,
                            mapper,
                            range,
                            fetchState,
                            resultConsumer);
                }
            }
        });
    }

    private StoredValues readValues(final ByteBuffer valueBuffer) {
        try (final DataReader reader =
                new KryoDataReader(new ByteBufferInput(valueBuffer))) {
            return valueReferenceIndex.read(reader);
        }
    }

    // --------------------------------------------------------------------------------


    private static class LmdbReadContext {

        private final LmdbDataStore dataStore;
        private final LmdbDb db;
        private final ReadTxn readTxn;
        private final TimeFilter timeFilter;

        public LmdbReadContext(final LmdbDataStore dataStore,
                               final LmdbDb db,
                               final ReadTxn readTxn,
                               final TimeFilter timeFilter) {
            this.dataStore = dataStore;
            this.db = db;
            this.readTxn = readTxn;
            this.timeFilter = timeFilter;
        }

        public <R> R readResult(final KeyRange<ByteBuffer> keyRange,
                                final Function<Iterator<KeyVal<ByteBuffer>>, R> iteratorConsumer) {
            return db.iterateResult(readTxn, keyRange, iteratorConsumer);
        }

        public void read(final KeyRange<ByteBuffer> keyRange,
                         final Consumer<Iterator<KeyVal<ByteBuffer>>> iteratorConsumer) {
            db.iterate(readTxn, keyRange, iteratorConsumer);
        }

        public Val createValue(final Key key,
                               final StoredValues storedValues,
                               final int index) {
            Val val;
            if (!dataStore.compiledDepths.getValueIndicesByDepth()[key.getDepth()][index]) {
                val = ValNull.INSTANCE;

            } else {
                final Generator generator = dataStore.compiledColumnArray[index].getGenerator();
                if (key.isGrouped()) {
                    final Supplier<ChildData> childDataSupplier = () -> {
                        // If we don't have any children at the requested depth then return null.
                        if (dataStore.compiledSorters.length <= key.getChildDepth()) {
                            return null;
                        }

                        return new ChildDataImpl(this, key);
                    };
                    val = generator.eval(storedValues, childDataSupplier);
                } else {
                    val = generator.eval(storedValues, null);
                }
            }
            return val;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ChildDataImpl implements ChildData {

        private final LmdbReadContext readContext;
        private final Key key;

        public ChildDataImpl(final LmdbReadContext readContext,
                             final Key key) {
            this.readContext = readContext;
            this.key = key;
        }

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
            return countChildren(
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
            final List<StoredValues> result = new ArrayList<>();
            final FetchState fetchState = new FetchState();
            fetchState.countRows = false;
            fetchState.reachedRowLimit = false;
            fetchState.keepGoing = true;

            final LmdbDataStore dataStore = readContext.dataStore;
            dataStore.getChildren(
                    readContext,
                    key,
                    key.getChildDepth(),
                    limit,
                    trimTop,
                    OpenGroups.NONE, // Don't traverse any child rows.
                    readContext.timeFilter,
                    IdentityItemMapper.INSTANCE,
                    OffsetRange.ZERO_1000, // Max 1000 child items.
                    fetchState,
                    item -> result.add(((ItemImpl) item).storedValues));
            return result;
        }

        private long countChildren(final Key parentKey,
                                   final int depth) {
            // If we don't have any children at the requested depth then return 0.
            final LmdbDataStore dataStore = readContext.dataStore;
            if (dataStore.compiledSorters.length <= depth) {
                return 0;
            }

            final AtomicLong atomicLong = new AtomicLong();
            dataStore.lmdbRowKeyFactory.createChildKeyRange(parentKey, keyRange ->
                    atomicLong.set(readContext.readResult(keyRange, iterator -> {
                        long count = 0;
                        while (iterator.hasNext()
                                && !Thread.currentThread().isInterrupted()) {
                            iterator.next();
                            // FIXME : NOTE THIS COUNT IS NOT FILTERED BY THE MAPPER.
                            count++;
                        }
                        return count;
                    })));
            return atomicLong.get();
        }
    }


    // --------------------------------------------------------------------------------

    private static class FetchState {

        /**
         * The current result offset.
         */
        long offset;
        /**
         * The current result length.
         */
        long length;
        /**
         * Determine if we are going to look through the whole store to count rows even when we have a result page
         */
        boolean countRows;
        /**
         * Once we have enough results we can just count results after
         */
        boolean justCount;
        /**
         * Track the total row count.
         */
        long totalRowCount;
        /**
         * Set to true once we no longer need any more results.
         */
        boolean reachedRowLimit;
        /**
         * Set to false if we don't want to keep looking through the store.
         */
        boolean keepGoing;
    }


    // --------------------------------------------------------------------------------


    private static class SortableItem implements Item {

        private final ItemImpl item;
        private final Val[] values;

        public SortableItem(final ItemImpl item, final Val[] values) {
            this.item = item;
            this.values = values;
        }

        @Override
        public Key getKey() {
            return item.key;
        }

        @Override
        public Val getValue(final int index) {
            return values[index];
        }
    }


    // --------------------------------------------------------------------------------


    private static class SortedItems {

        private final int minArraySize;
        private final int maxArraySize;
        private final int trimmedSize;
        private final boolean trimTop;
        private final CompiledSorter<Item> sorter;
        private ItemImpl[] array;
        private int size;
        private boolean trimmed = true;

        public SortedItems(final int minArraySize,
                           final int maxArraySize,
                           final int trimmedSize,
                           final boolean trimTop,
                           final CompiledSorter<Item> sorter) {
            this.minArraySize = minArraySize;
            this.maxArraySize = maxArraySize;
            this.trimmedSize = trimmedSize;
            this.trimTop = trimTop;
            this.sorter = sorter;
            array = new ItemImpl[minArraySize];
        }

        private void sortAndTrim() {
            if (sorter != null && size > 0) {
                sort(sorter);
            }

            if (size > trimmedSize) {
                trim(trimmedSize, trimTop);
            }

            trimmed = true;
        }

        private void sort(final CompiledSorter<Item> sorter) {
            int maxFieldIndex = -1;
            final List<CompiledSort> compiledSorts = sorter.getCompiledSorts();
            for (final CompiledSort compiledSort : compiledSorts) {
                final int fieldIndex = compiledSort.getFieldIndex();
                maxFieldIndex = Math.max(maxFieldIndex, fieldIndex);
            }

            final SortableItem[] sortableItems = new SortableItem[size];
            // Resolve all values before sorting.
            for (int i = 0; i < size; i++) {
                final ItemImpl item = array[i];
                final Val[] values = new Val[maxFieldIndex + 1];
                for (final CompiledSort compiledSort : compiledSorts) {
                    final int fieldIndex = compiledSort.getFieldIndex();
                    values[fieldIndex] = item.getValue(fieldIndex);
                }
                sortableItems[i] = new SortableItem(item, values);
            }
            // Sort.
            Arrays.sort(sortableItems, sorter);
            // Put sorted items back in the array.
            for (int i = 0; i < size; i++) {
                array[i] = sortableItems[i].item;
            }
        }

        private void trim(final int trimmedSize,
                          final boolean trimTop) {
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

        void add(final ItemImpl item) {
            if (array.length <= size) {
                final ItemImpl[] newArray = new ItemImpl[size * 2];
                System.arraycopy(array, 0, newArray, 0, array.length);
                array = newArray;
            }
            array[size++] = item;

            trimmed = false;
            if (size > maxArraySize) {
                sortAndTrim();
            }
        }

        public Iterable<Item> getIterable() {
            if (!trimmed) {
                sortAndTrim();
            }

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


    // --------------------------------------------------------------------------------


    private static class ItemImpl implements Item {

        private final LmdbReadContext readContext;
        private final Key key;
        private final StoredValues storedValues;

        public ItemImpl(final LmdbReadContext readContext,
                        final Key key,
                        final StoredValues storedValues) {
            this.readContext = readContext;
            this.key = key;
            this.storedValues = storedValues;
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index) {
            return readContext.createValue(key, storedValues, index);
        }
    }


    // --------------------------------------------------------------------------------


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
}
