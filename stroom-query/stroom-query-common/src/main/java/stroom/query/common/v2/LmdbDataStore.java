/*
 * Copyright 2020 Crown Copyright
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
import stroom.dictionary.api.WordListProvider;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.ReadTxn;
import stroom.lmdb2.WriteTxn;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.HoppingWindow;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.ChildData;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.TimeWindows;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.WindowRowGenerator;
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
import stroom.util.logging.SimpleMetrics;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jakarta.inject.Provider;
import org.lmdbjava.Env.MapFullException;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.LmdbException;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class LmdbDataStore implements DataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDataStore.class);

    private static final long COMMIT_FREQUENCY_MS = 10000;
    private static final long WAIT_WARN_FREQUENCY_MS = 30_000;
    private static final int MAX_LIST_SIZE = 10_000;

    private final LmdbEnv env;
    // The env is built with MDB_NOTLS and maxReaders(1), so only one read txn may exist at a
    // time. Client reads are serialised by this object's monitor, but the transfer thread
    // reads the DB state without holding it (it cannot: close() holds the monitor while
    // waiting for the transfer thread to finish), so reads take this lock instead. Not held
    // by close() while it waits, so it can never delay the thread close() is waiting for.
    private final ReentrantLock readTxnLock = new ReentrantLock();
    private final LmdbDb db;
    private final LmdbDb stateDb;
    private final ValueReferenceIndex valueReferenceIndex;
    private final CompiledColumns compiledColumns;
    private final CompiledColumn[] compiledColumnArray;
    private final CompiledSorters<Item> compiledSorters;
    private final CompiledDepths compiledDepths;
    private final LmdbPutFilter putFilter;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final Predicate<Val[]> valueFilter;

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
    // The time windows of the table's window clause and where its time field is in a row, or null
    // and -1 if it has none.
    private final TimeWindows timeWindows;
    // How far each row is copied for the functions that read other windows, such as lag.
    private final int[] windowShifts;
    // Gives the window key of a stored row, for a table whose rows are copied to other windows.
    private final WindowRowGenerator windowKeyGenerator;
    // The first and last windows that the store has real rows for, noted as rows are stored.
    private final StoredWindowExtent storedWindowExtent = new StoredWindowExtent();
    // Counts the times the store has been read through to find its windows.
    private final AtomicLong windowExtentScans = new AtomicLong();
    private final int windowTimeFieldIndex;
    private final String windowTimeField;
    private final AtomicBoolean reportedRowWithNoWindowTime = new AtomicBoolean();
    private final AtomicLong rowsWithNoWindowTime = new AtomicLong();
    // A store that is added to for as long as its rule runs is never complete, so can't wait until
    // then to say how many rows it left out.
    private final boolean neverComplete;
    private final StoredValueMapper storedValueMapper;

    private final int maxPutsBeforeCommit;
    private final CurrentDbStateFactory currentDbStateFactory;

    private final StoredValueKeyFactory storedValueKeyFactory;
    private final int maxSortedItems;
    private final DateTimeSettings dateTimeSettings;
    private final ByteBufferFactory bufferFactory;
    private final ByteBuffer dbStateValue = ByteBuffer
            .allocateDirect(Long.BYTES + Long.BYTES + Long.BYTES);

    public LmdbDataStore(final SearchRequestSource searchRequestSource,
                         final LmdbEnv.Builder lmdbEnvBuilder,
                         final AbstractResultStoreConfig resultStoreConfig,
                         final QueryKey queryKey,
                         final String componentId,
                         final TableSettings tableSettings,
                         final ExpressionContext expressionContext,
                         final FieldIndex fieldIndex,
                         final Map<String, String> paramMap,
                         final DataStoreSettings dataStoreSettings,
                         final Provider<Executor> executorProvider,
                         final ErrorConsumer errorConsumer,
                         final ByteBufferFactory bufferFactory,
                         final ExpressionPredicateFactory expressionPredicateFactory,
                         final AnnotationMapperFactory annotationMapperFactory,
                         final WordListProvider wordListProvider) {
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

        neverComplete = SourceType.TABLE_BUILDER_ANALYTIC.equals(sourceType);
        if (tableSettings.getWindow() instanceof final HoppingWindow hoppingWindow) {
            timeWindows = createTimeWindows(hoppingWindow);
            windowTimeField = hoppingWindow.getTimeField();
            windowTimeFieldIndex = fieldIndex.create(windowTimeField);
        } else {
            timeWindows = null;
            windowTimeField = null;
            windowTimeFieldIndex = -1;
        }
        final List<Column> columns = Objects
                .requireNonNullElse(tableSettings.getColumns(), Collections.emptyList());
        queue = new LmdbWriteQueue(resultStoreConfig.getValueQueueSize(), bufferFactory);
        maxSortedItems = resultStoreConfig.getMaxSortedItems();
        this.dateTimeSettings = expressionContext == null
                ? null
                : expressionContext.getDateTimeSettings();
        this.compiledColumns = CompiledColumns.create(expressionContext, columns, fieldIndex, paramMap);
        this.compiledColumnArray = compiledColumns.getCompiledColumns();
        valueReferenceIndex = compiledColumns.getValueReferenceIndex();
        windowShifts = valueReferenceIndex.getWindowShifts();
        checkWindowUse(resultStoreConfig);
        // The generator is only needed to find the range of windows, for functions that read others.
        final WindowRowGenerator windowRowGenerator = timeWindows == null
                ? null
                : findWindowKeyGenerator();
        windowKeyGenerator = windowShifts.length == 0
                ? null
                : windowRowGenerator;
        compiledDepths = new CompiledDepths(this.compiledColumnArray, tableSettings.showDetail());
        compiledSorters = new CompiledSorters<>(compiledDepths, columns);
        final int maxStringFieldLength = tableSettings.overrideMaxStringFieldLength() &&
                                         tableSettings.getMaxStringFieldLength() != null
                ?
                tableSettings.getMaxStringFieldLength()
                : resultStoreConfig.getMaxStringFieldLength();
        writerFactory = new DataWriterFactory(errorConsumer, maxStringFieldLength);
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
                .maxDbs(2)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .maxReaders(1)
                .errorHandler(this::error)
                .build();
        this.db = env.openDb(queryKey + "_" + componentId);
        this.stateDb = env.openDb("state");
        if (db.count() == 0) {
            storedWindowExtent.openedEmpty();
        }

        // Create a filter for incoming data.
        valueFilter = ValFilter.create(
                tableSettings.getValueFilter(),
                compiledColumns,
                dateTimeSettings,
                expressionPredicateFactory,
                paramMap, wordListProvider);

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

        // Create a mapper to add annotation data.
        storedValueMapper = annotationMapperFactory.createMapper(valueReferenceIndex);

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
        if (valueFilter.test(values)) {
            // Now add the rows if we aren't filtering.
            if (timeWindows == null) {
                addInternal(values, 0, 0, currentDbStateFactory.createCurrentDbState(values));
            } else {
                addToWindows(values);
            }
        }
    }

    // Finds the column that rows are grouped into windows by, which is the window key on its own.
    // It must be in the outermost group, so that every row of the store is in one window. Every row
    // is copied into each of its windows, so unless rows are grouped by window the copies would be
    // counted together.
    private WindowRowGenerator findWindowKeyGenerator() {
        for (final CompiledColumn compiledColumn : compiledColumnArray) {
            final Integer group = compiledColumn.getColumn().getGroup();
            if (group != null
                && group == 0
                && compiledColumn.getGenerator() instanceof final WindowRowGenerator windowRowGenerator) {
                return windowRowGenerator;
            }
        }
        throw new IllegalArgumentException("A table with a window must have a column that is grouped by "
                                           + "windowKey() in its outermost group");
    }

    // Sets the first and last windows that hold a real row, and not only copies of rows from other
    // windows. The range is of the whole store, whatever span of time is being read from it, as a
    // window can read one that is outside that span.
    //
    // The windows are noted as rows are stored. The store is only read through to find them when it
    // has changed in a way that was not noted, such as rows being deleted.
    //
    // The extent is what was known before the read began. If it were taken after, it could be of
    // rows that were stored since, which the read does not see.
    private void setWindowRange(final LmdbReadContext readContext, final StoredWindowExtent.Snapshot extent) {
        if (windowKeyGenerator == null) {
            return;
        }
        final long first;
        final long last;
        if (!StoredWindowExtent.isSettled(extent, storedWindowExtent.snapshot())) {
            // Rows were committed as the read began, and it can't be told whether the read sees
            // them, so find the windows of the rows that it does see. They are not kept.
            final long[] range = scanWindowExtent(readContext);
            if (range == null) {
                return;
            }
            first = range[0];
            last = range[1];
        } else {
            if (storedWindowExtent.needsScan(extent)) {
                final long[] range = scanWindowExtent(readContext);
                if (range == null) {
                    // The rows were not all read, so the range is worked out again next time.
                    return;
                }
                storedWindowExtent.scanned(extent, range[0], range[1]);
            }
            first = storedWindowExtent.getFirst(extent);
            last = storedWindowExtent.getLast(extent);
        }
        if (first > last) {
            valueReferenceIndex.getWindowRange().clear();
        } else {
            valueReferenceIndex.getWindowRange().set(first, last);
        }
    }

    // Reads through the top level rows of the store to find the first and last windows with a real
    // row. Returns null if the read was interrupted.
    private long[] scanWindowExtent(final LmdbReadContext readContext) {
        windowExtentScans.incrementAndGet();
        final long[] range = new long[]{Long.MAX_VALUE, Long.MIN_VALUE};
        lmdbRowKeyFactory.createChildKeyRange(Key.ROOT_KEY, keyRange ->
                readContext.read(keyRange, iterator -> {
                    while (iterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                        final ByteBuffer valueBuffer = iterator.next().getVal();
                        do {
                            final StoredValues storedValues = readValues(valueBuffer);
                            final Long windowKey = windowKeyGenerator.getWindowKey(storedValues);
                            if (windowKey != null && windowKeyGenerator.hasRealRows(storedValues)) {
                                range[0] = Math.min(range[0], windowKey);
                                range[1] = Math.max(range[1], windowKey);
                            }
                        } while (valueBuffer.hasRemaining());
                    }
                }));
        return Thread.currentThread().isInterrupted()
                ? null
                : range;
    }

    // Reads the rows held in a value, leaving out any that exist only because a row was copied to a
    // window before the first with data or after the last.
    private Stream<StoredValues> readRows(final ByteBuffer valueBuffer) {
        final StoredValues storedValues = readValues(valueBuffer);
        if (windowKeyGenerator != null) {
            final Long windowKey = windowKeyGenerator.getWindowKey(storedValues);
            if (windowKey != null && !valueReferenceIndex.getWindowRange().contains(windowKey)) {
                return Stream.empty();
            }
        }
        return storedValueMapper.create(storedValues);
    }

    private void checkWindowUse(final AbstractResultStoreConfig resultStoreConfig) {
        if (timeWindows == null) {
            if (valueReferenceIndex.usesWindows()) {
                throw new IllegalArgumentException("Window functions such as windowStart() and lag() can "
                                                   + "only be used in a query that has a window clause");
            }
        } else {
            // StroomQL applies the configured limit to every query. It is checked again here for
            // the results of a search, whose table need not come from StroomQL. The table of a rule
            // always does, and its store has no such setting, so there is nothing more to check.
            final int maxCopies = resultStoreConfig instanceof final SearchResultStoreConfig searchConfig
                    ? searchConfig.getMaxWindowCopiesPerEvent()
                    : Integer.MAX_VALUE;
            final long copies = TimeWindows.getCopiesPerTime(timeWindows.getWindowsPerTime(), windowShifts.length);
            if (copies > maxCopies) {
                throw new IllegalArgumentException(TimeWindows.tooManyCopiesMessage(
                        timeWindows.getWindowsPerTime(), windowShifts.length, maxCopies));
            }
        }
    }

    // StroomQL only gives a table windows that fit these rules, but a table can also come from a
    // request that was not parsed from StroomQL, so they are checked again here.
    private static TimeWindows createTimeWindows(final HoppingWindow window) {
        if (NullSafe.isBlankString(window.getTimeField())) {
            throw new IllegalArgumentException("The window of a table has no time field");
        }
        return TimeWindows.create(window);
    }

    /// Gets the latest row time that a rule may judge, once events have arrived up to a time. A rule
    /// judges a row of a table with time windows only when the row's window is closed, which is when
    /// the window has ended and so have the later windows that the row reads with `lead`. A window
    /// that is still filling would be judged on part of its events. The time of such a row is the
    /// start of its window.
    ///
    /// @param timeMs The time that events have arrived up to, in epoch milliseconds.
    /// @return The start of the latest closed window, or the time given if the table has no windows.
    public long getClosedWindowTime(final long timeMs) {
        if (timeWindows == null) {
            return timeMs;
        }
        // A lead is held as a shift to an earlier window, which is the window that reads the row.
        int furthestLead = 0;
        for (final int windowShift : windowShifts) {
            furthestLead = Math.min(furthestLead, windowShift);
        }
        final long closed = timeWindows.shift(timeWindows.getLatestEndedWindow(timeMs), furthestLead);
        final long closedStart = timeWindows.getWindowStart(closed);
        // When the clocks go forward, two windows can start at the same time and end at different
        // ones. Rows are read by their time, so if the next window starts with this one, neither is
        // read until both are closed.
        return timeWindows.getWindowStart(timeWindows.getNextWindow(closed)) == closedStart
                ? closedStart - 1
                : closedStart;
    }

    /// Gets the latest row time that may be deleted from a table with time windows that a rule
    /// judges. A window is kept until the rule has judged it, and for as long after as a later
    /// window that is still to be judged reads it with `lag`.
    ///
    /// @param judgedTimeMs The latest row time that the rule has judged, or `null` if it has judged
    ///                     none.
    /// @return The latest row time that may be deleted, which is `Long.MAX_VALUE` if the table has
    ///         no windows and is before any row if nothing may be deleted.
    public long getDeletableWindowTime(final Long judgedTimeMs) {
        if (timeWindows == null) {
            return Long.MAX_VALUE;
        }
        if (judgedTimeMs == null) {
            return Long.MIN_VALUE;
        }
        int furthestLag = 0;
        for (final int windowShift : windowShifts) {
            furthestLag = Math.max(furthestLag, windowShift);
        }
        // The latest window that covers the time is the latest to have been judged.
        final long[] judged = new long[1];
        timeWindows.forEachWindow(judgedTimeMs, windowKey -> judged[0] = windowKey);
        return timeWindows.getWindowStart(timeWindows.shift(judged[0], -furthestLag)) - 1;
    }

    // Adds a row once for each time window it is in, so that it is grouped into each of them.
    private void addToWindows(final Val[] values) {
        final Val time = windowTimeFieldIndex < values.length
                ? values[windowTimeFieldIndex]
                : null;
        final Long timeMs = time == null
                ? null
                : time.toLong();
        if (timeMs == null) {
            // A row with no time can't be put in a window, so it is left out. How many are left out
            // is said once, when no more rows will be added.
            rowsWithNoWindowTime.incrementAndGet();
            if (neverComplete && reportedRowWithNoWindowTime.compareAndSet(false, true)) {
                errorConsumer.add(Severity.WARNING, () -> "Rows with no time in the window field '"
                                                          + windowTimeField + "' are left out of the results");
            }
            return;
        }
        final CurrentDbState currentDbState = currentDbStateFactory.createCurrentDbState(values);
        timeWindows.forEachWindow(timeMs, windowKey -> {
            addInternal(values, windowKey, 0, currentDbState);
            // Copy the row to each window that reads this one.
            for (final int windowShift : windowShifts) {
                addInternal(values, timeWindows.shift(windowKey, windowShift), windowShift, currentDbState);
            }
        });
    }

    private void reportRowsWithNoWindowTime() {
        final long count = rowsWithNoWindowTime.get();
        if (count > 0 && reportedRowWithNoWindowTime.compareAndSet(false, true)) {
            errorConsumer.add(Severity.WARNING, () ->
                    count + (count == 1
                            ? " row"
                            : " rows")
                    + " with no time in the window field '" + windowTimeField
                    + (count == 1
                            ? "' was"
                            : "' were")
                    + " left out of the results");
        }
    }

    // The current DB state is a reference to the current event, so we can keep track of what we have
    // stored data for.
    private void addInternal(final Val[] values,
                             final long windowKey,
                             final int windowShift,
                             final CurrentDbState currentDbState) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_ADD);
        LOGGER.trace("add() called for {} values", values.length);
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        // A row copied from another window adds to the grouped rows of the window that reads it, but
        // is not one of that window's own rows, so it is left out of the depth below the groups that
        // holds each row.
        final int depths = windowShift == 0
                ? groupIndicesByDepth.length
                : Math.min(groupIndicesByDepth.length, compiledDepths.getMaxGroupDepth() + 1);

        ByteBuffer parentRowKey = null;
        final LmdbKV[] rows = new LmdbKV[depths];
        for (int depth = 0; depth < depths; depth++) {
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            storedValues.setWindowKey(windowKey);
            storedValues.setWindowShift(windowShift);
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            for (int columnIndex = 0; columnIndex < compiledColumnArray.length; columnIndex++) {
                final CompiledColumn compiledColumn = compiledColumnArray[columnIndex];
                final Generator generator = compiledColumn.getGenerator();

                // If we need a value at this level then set the raw values.
                if (valueIndices[columnIndex] ||
                    columnIndex == keyFactoryConfig.getTimeColumnIndex()) {
                    // A row copied from another window is grouped like any other, but is not counted
                    // by the aggregates of the window it is copied to.
                    if (windowShift == 0 || groupIndices[columnIndex] || !compiledColumn.hasAggregate()) {
                        generator.set(values, storedValues);
                    } else {
                        generator.setShifted(values, storedValues);
                    }
                }
            }

            final ByteBuffer rowKey = lmdbRowKeyFactory.create(depth, parentRowKey, storedValues);
            final ByteBuffer rowValue = lmdbRowValueFactory.create(storedValues);
            parentRowKey = rowKey;
            // Note the window of a top level row of an event in the window, so that the first and
            // last windows with such rows are known without reading through the store.
            final Long realWindowKey = timeWindows != null && depth == 0 && windowShift == 0
                    ? Long.valueOf(windowKey)
                    : null;
            rows[depth] = new LmdbKV(currentDbState, rowKey, rowValue, realWindowKey);
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

    /// Notes the windows of the rows of a payload from another node. It is put on the queue ahead of
    /// the rows, and is not counted as a row by the put filter.
    ///
    /// @param span The windows that the other node holds real rows for.
    void putWindowSpan(final StoredWindowExtent.Span span) {
        doPut(span);
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
        SimpleMetrics.measure("Transfer", () -> {
            try {
                transferState.setThread(Thread.currentThread());
                env.write(writeTxn -> {
                    // Must be the unsynchronised read: close() holds this store's monitor
                    // while waiting for this thread to finish, so taking the monitor here
                    // would deadlock. See readCurrentDbState().
                    CurrentDbState currentDbState = readCurrentDbState();
                    long lastCommitMs = System.currentTimeMillis();
                    long uncommittedCount = 0;

                    try {
                        while (!transferState.isTerminated()) {
                            LOGGER.trace(() -> "Transferring");
                            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_QUEUE_POLL);
                            final LmdbQueueItem queueItem = queue.poll(1, TimeUnit.SECONDS);

                            if (queueItem != null) {
                                switch (queueItem) {
                                    case final LmdbKV lmdbKV -> {
                                        currentDbState = lmdbKV.getCurrentDbState();
                                        insert(writeTxn, db, lmdbKV);
                                        storedWindowExtent.add(lmdbKV);
                                        uncommittedCount++;
                                    }
                                    case final CurrentDbStateLmdbQueueItem currentDbStateLmdbQueueItem -> {
                                        currentDbState = currentDbStateLmdbQueueItem.getCurrentDbState()
                                                .mergeExisting(currentDbState);
                                    }
                                    case final Sync sync -> {
                                        commit(writeTxn, currentDbState);
                                        sync.sync();
                                    }
                                    case final DeleteCommand deleteCommand -> delete(writeTxn, deleteCommand);
                                    case final StoredWindowExtent.Span span -> storedWindowExtent.addSpan(span);
                                    default -> {
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
                                storedWindowExtent.beginCommit();
                                payloadCreator.addPayload(
                                        writeTxn,
                                        db,
                                        false,
                                        storedWindowExtent.getWrittenSpan());
                                // The payload takes the rows out of the store.
                                storedWindowExtent.committed(true);

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
                            finalPayload = payloadCreator.addPayload(
                                    writeTxn,
                                    db,
                                    true,
                                    storedWindowExtent.getWrittenSpan());
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
                // Count down first. close() waits on this latch uninterruptibly before
                // closing the env, so nothing that can throw may run ahead of it — the
                // env.write() above has returned by now, so the write txn is already closed.
                complete.countDown();
                // Ensure we complete.
                queue.terminate();
                LOGGER.debug(() -> "Finished transfer while loop");
                transferState.setThread(null);
            }
        });
    }

    private void delete(final WriteTxn writeTxn,
                        final DeleteCommand deleteCommand) {
        lmdbRowKeyFactory.createChildKeyRange(
                deleteCommand.getParentKey(), deleteCommand.getTimeFilter(), keyRange -> {
                    final boolean[] rowsDeleted = new boolean[1];
                    storedWindowExtent.beginCommit();
                    try (final Stream<LmdbEntry> stream = db.stream(writeTxn, keyRange)) {
                        stream.forEach(entry -> {
                            final ByteBuffer keyBuffer = entry.getKey();
                            if (keyBuffer.limit() > 1) {
                                db.delete(writeTxn, keyBuffer);
                                rowsDeleted[0] = true;
                            }
                        });
                    }
                    writeTxn.commit();
                    storedWindowExtent.committed(rowsDeleted[0]);
                });
    }

    private void commit(final WriteTxn writeTxn,
                        final CurrentDbState currentDbState) {
        putCurrentDbState(writeTxn, currentDbState);
        storedWindowExtent.beginCommit();
        writeTxn.commit();
        storedWindowExtent.committed(false);
    }


    private void insert(final WriteTxn writeTxn,
                        final LmdbDb db,
                        final LmdbKV lmdbKV) {
        SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_INSERT);
        SimpleMetrics.measure("Insert", () -> {
            try {
                LOGGER.trace(() -> "insert");

                // Just try to put first.
                final boolean success = put(
                        writeTxn,
                        db,
                        lmdbKV.key(),
                        lmdbKV.val(),
                        PutFlags.MDB_NOOVERWRITE);
                if (success) {
                    resultCount.incrementAndGet();

                } else {
                    final int depth = lmdbRowKeyFactory.getDepth(lmdbKV);
                    if (lmdbRowKeyFactory.isGroup(depth)) {
                        final StoredValues newStoredValues = readValues(lmdbKV.val().duplicate());
                        final Val[] newGroupValues
                                = storedValueKeyFactory.getGroupValues(depth, newStoredValues);

                        // Get the existing entry for this key.
                        final ByteBuffer existingValueBuffer = db.get(writeTxn, lmdbKV.key());
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
                                output.writeByteBuffer(lmdbKV.val());
                                resultCount.incrementAndGet();
                            }
                        });

                        final boolean ok = put(writeTxn, db, lmdbKV.key(), newValueBuffer);
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
                bufferFactory.release(lmdbKV.key());
                bufferFactory.release(lmdbKV.val());
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

            // Wait for transferring to stop, and keep waiting even if we are interrupted.
            // Terminating a search interrupts its task threads, so an interrupt here is
            // routine rather than exceptional, and we must still close the env: clear()
            // deletes the env dir immediately after this, so bailing out early would leak
            // result store dirs on disk for every terminated search.
            // Waiting is what makes closing the env safe. The transfer thread counts down
            // `complete` in a finally that runs after env.write() has returned, i.e. once
            // its write txn is closed, and it reaches that finally even when interrupted
            // itself. The wait is short: the terminate calls above stop the transfer loop
            // and wake it from its queue poll.
            boolean interrupted = false;
            try {
                LOGGER.debug(() -> "Waiting for transfer to stop");
                while (true) {
                    try {
                        if (completionState.awaitCompletion(WAIT_WARN_FREQUENCY_MS, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                        // Still unbounded, but a transfer thread wedged in native LMDB would
                        // otherwise be an unattributable hang.
                        LOGGER.warn(() -> "Still waiting for transfer to stop (queryKey=" +
                                          queryKey + ", componentId=" + componentId + ")");
                    } catch (final InterruptedException e) {
                        LOGGER.trace(e::getMessage, e);
                        interrupted = true;
                    }
                }

                env.close();
            } finally {
                if (interrupted) {
                    // Keep interrupting this thread.
                    Thread.currentThread().interrupt();
                }
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
            try {
                close();
            } catch (final RuntimeException e) {
                // Log rather than propagate so the delete below is still attempted: if the
                // env did close before the failure then bailing out here would leak the
                // store dir on disk forever, and shutdown is already latched so a retried
                // clear() would not close it either.
                LOGGER.error(e::getMessage, e);
            }
            // Refuses to delete an env that is still open, so this can never delete under a
            // live env; it throws instead, surfacing a genuinely failed close.
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

    /// Gets how many times the store has been read through to find the first and last time windows
    /// that it holds real rows for. Tests use it to check that the windows are noted as rows are
    /// stored.
    ///
    /// @return The number of times.
    long getWindowExtentScanCount() {
        return windowExtentScans.get();
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

    public CompiledColumns getCompiledColumns() {
        return compiledColumns;
    }

    private void putCurrentDbState(final WriteTxn writeTxn, final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            ByteBuffer valueBuffer = dbStateValue;
            valueBuffer.clear();
            putLong(valueBuffer, currentDbState.getStreamId());
            putLong(valueBuffer, currentDbState.getEventId());
            putLong(valueBuffer, currentDbState.getLastEventTime());
            valueBuffer = valueBuffer.flip();

            final boolean success = put(
                    writeTxn,
                    stateDb,
                    LmdbRowKeyFactoryFactory.DB_STATE_KEY,
                    valueBuffer);
            if (!success) {
                LOGGER.error("Unable to store event ref");
            }
        }
    }

    /**
     * For client threads. Synchronised because this store's monitor is all that keeps client
     * reads and close()'s env.close() apart (the lmdb2 env has no reader tracking of its own).
     */
    private synchronized CurrentDbState getCurrentDbState() {
        return readCurrentDbState();
    }

    /**
     * Deliberately NOT synchronised, for use by the transfer thread only. close() holds this
     * store's monitor while it waits for the transfer thread to exit, so a synchronised call
     * from the transfer thread would deadlock: close() waits on the complete latch that only
     * counts down when the transfer thread finishes. The read is still safe against env close
     * because close() only closes the env after that latch, i.e. strictly after the transfer
     * thread has finished. Client threads must use {@link #getCurrentDbState()}.
     */
    private CurrentDbState readCurrentDbState() {
        if (!currentDbStateFactory.isStoreLatestEventReference()) {
            return null;
        }

        readTxnLock.lock();
        try {
            return doReadCurrentDbState();
        } finally {
            readTxnLock.unlock();
        }
    }

    private CurrentDbState doReadCurrentDbState() {
        return env.readResult(readTxn -> {
            final ByteBuffer val = stateDb.get(readTxn, LmdbRowKeyFactoryFactory.DB_STATE_KEY);
            if (val == null) {
                return null;
            }

            final long streamId = Objects.requireNonNull(getLong(val, 0),
                    "streamId not present in DB state");
            final Long eventId = getLong(val, Long.BYTES);
            final Long lastEventTime = getLong(val, Long.BYTES + Long.BYTES);
            return new CurrentDbState(streamId, eventId, lastEventTime);
        });
    }

    private Long getLong(final ByteBuffer valueBuffer, final int index) {
        final long l = valueBuffer.getLong(index);
        if (l == -1) {
            return null;
        }
        return l;
    }

    private void putLong(final ByteBuffer valueBuffer, final Long l) {
        valueBuffer.putLong(Objects.requireNonNullElse(l, -1L));
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

    @Override
    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    @Override
    public synchronized void fetch(final List<Column> columns,
                                   final OffsetRange range,
                                   final OpenGroups openGroups,
                                   final TimeFilter timeFilter,
                                   final ItemMapper mapper,
                                   final Consumer<Item> resultConsumer,
                                   final Consumer<Long> totalRowCountConsumer) {
        // Update our sort columns if needed.
        compiledSorters.update(columns);

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
            // Only one read txn may exist at a time (maxReaders(1)); the transfer thread
            // reads the DB state without holding this object's monitor, so share the lock.
            readTxnLock.lock();
            try {
                final StoredWindowExtent.Snapshot windowExtentBeforeRead = storedWindowExtent.snapshot();
                env.read(readTxn ->
                        SimpleMetrics.measure("fetch", () -> {
                            try {
                                final FetchState fetchState = new FetchState();
                                fetchState.countRows = totalRowCountConsumer != null;
                                fetchState.reachedRowLimit = fetchState.length >= enforcedRange.getLength();
                                fetchState.keepGoing = fetchState.justCount || !fetchState.reachedRowLimit;

                                final LmdbReadContext readContext = new LmdbReadContext(
                                        LmdbDataStore.this,
                                        db,
                                        readTxn,
                                        timeFilter);
                                setWindowRange(readContext, windowExtentBeforeRead);

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
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                                throw e;
                            }
                        }));
            } finally {
                readTxnLock.unlock();
            }
        }
    }

    private void getChildren(final LmdbReadContext readContext,
                             final Key parentKey,
                             final int depth,
                             final long limit,
                             final boolean trimTop,
                             final OpenGroups openGroups,
                             final TimeFilter timeFilter,
                             final ItemMapper mapper,
                             final OffsetRange range,
                             final FetchState fetchState,
                             final Consumer<Item> resultConsumer) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.LMDB_DATA_STORE_GET_CHILDREN);

            // If we aren't sorting then just return results directly.
            if (fetchState.justCount || compiledSorters.get(depth) == null) {
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


    private void getUnsortedChildren(final LmdbReadContext readContext,
                                     final Key parentKey,
                                     final int depth,
                                     final long limit,
                                     final OpenGroups openGroups,
                                     final TimeFilter timeFilter,
                                     final ItemMapper mapper,
                                     final OffsetRange range,
                                     final FetchState fetchState,
                                     final Consumer<Item> resultConsumer) {
        // Reduce the number of groups left to go into.
        openGroups.complete(parentKey);

        lmdbRowKeyFactory.createChildKeyRange(parentKey, timeFilter, keyRange ->
                readContext.read(keyRange, iterator -> {
                    final AtomicLong childCount = new AtomicLong();
                    while (fetchState.keepGoing &&
                           childCount.get() < limit &&
                           iterator.hasNext() &&
                           !Thread.currentThread().isInterrupted()) {

                        final LmdbEntry keyVal = iterator.next();
                        final ByteBuffer keyBuffer = keyVal.getKey();
                        final ByteBuffer valueBuffer = keyVal.getVal();

                        // If we are just counting the total results from this point then we don't need to
                        // deserialise unless we are hiding rows.
                        if (fetchState.justCount) {
                            // Rows outside the range of windows are only at the top level.
                            if (mapper.hidesRows() || (windowKeyGenerator != null && depth == 0)) {
                                final Stream<StoredValues> stream = readRows(valueBuffer);
                                stream.forEach(storedValues -> {
                                    final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                    final LmdbItem item = new LmdbItem(
                                            readContext,
                                            key,
                                            compiledColumns.getCompiledColumns(),
                                            storedValues);
                                    mapper.create(item).forEach(row -> {
                                        childCount.incrementAndGet();
                                        fetchState.totalRowCount++;

                                        // Add children if the group is open.
                                        final int childDepth = depth + 1;
                                        if (openGroups.isOpen(item.getKey()) &&
                                            compiledSorters.size() > childDepth) {
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
                                    });
                                });
                            } else {
                                childCount.incrementAndGet();
                                fetchState.totalRowCount++;

                                // Only bother to test open groups if we have some left.
                                if (openGroups.isNotEmpty()) {
                                    // Add children if the group is open.
                                    final Stream<StoredValues> stream = readRows(valueBuffer);
                                    stream.forEach(storedValues -> {
                                        final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                        final int childDepth = depth + 1;
                                        if (openGroups.isOpen(key) &&
                                            compiledSorters.size() > childDepth) {
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
                                    });
                                }
                            }

                        } else {
                            do {
                                final Stream<StoredValues> stream = readRows(valueBuffer);
                                stream.forEach(storedValues -> {
                                    final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                                    final LmdbItem item = new LmdbItem(
                                            readContext,
                                            key,
                                            compiledColumns.getCompiledColumns(),
                                            storedValues);
                                    mapper.create(item).forEach(row -> {
                                        childCount.incrementAndGet();
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
                                            compiledSorters.size() > childDepth) {
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
                                    });
                                });
                            } while (fetchState.keepGoing && valueBuffer.hasRemaining());
                        }
                    }
                }));
    }

    private void getSortedChildren(final LmdbReadContext readContext,
                                   final Key parentKey,
                                   final int depth,
                                   final long limit,
                                   final boolean trimTop,
                                   final OpenGroups openGroups,
                                   final TimeFilter timeFilter,
                                   final ItemMapper mapper,
                                   final OffsetRange range,
                                   final FetchState fetchState,
                                   final Consumer<Item> resultConsumer) {
        // Remember that we have gone into this group, so we don't keep trying.
        openGroups.complete(parentKey);

        final CompiledSorter<Item> sorter = compiledSorters.get(depth);

        lmdbRowKeyFactory.createChildKeyRange(parentKey, timeFilter, keyRange -> {
            final long lengthRemaining = range.getOffset() + range.getLength() - fetchState.length;
            final int trimmedSize = (int) Math.max(Math.min(Math.min(limit, lengthRemaining), maxSortedItems), 0);

            int maxSize = trimmedSize * 2;
            maxSize = Math.max(maxSize, 1_000);

            final SortedItems sortedItems = new SortedItems(64, maxSize, trimmedSize, trimTop, sorter);
            readContext.read(keyRange, iterator -> {
                final AtomicLong totalRowCount = new AtomicLong();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final LmdbEntry keyVal = iterator.next();

                    final ByteBuffer keyBuffer = keyVal.getKey();
                    final ByteBuffer valueBuffer = keyVal.getVal();
                    boolean isFirstValue = true;
                    // It is possible to have no actual values, e.g. if you have just one col of
                    // 'currentUser()' so we still need to create and add an empty storedValues
                    while (valueBuffer.hasRemaining() || isFirstValue) {
                        isFirstValue = false;

                        final Stream<StoredValues> stream = readRows(valueBuffer);
                        stream.forEach(storedValues -> {
                            final Key key = lmdbRowKeyFactory.createKey(parentKey, storedValues, keyBuffer);
                            final LmdbItem item = new LmdbItem(
                                    readContext,
                                    key,
                                    compiledColumns.getCompiledColumns(),
                                    storedValues);
                            mapper.create(item).forEach(row -> {
                                totalRowCount.incrementAndGet();
                                sortedItems.add(row);
                            });
                        });
                    }
                }

                // If there is a limit then pretend that the total row count is constrained.
                fetchState.totalRowCount += Math.min(totalRowCount.get(), limit);
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
                        resultConsumer.accept(item);
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
                    compiledSorters.size() > childDepth &&
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

        public <R> R readResult(final LmdbKeyRange keyRange,
                                final Function<Iterator<LmdbEntry>, R> iteratorConsumer) {
            return db.iterateResult(readTxn, keyRange, iteratorConsumer);
        }

        public void read(final LmdbKeyRange keyRange,
                         final Consumer<Iterator<LmdbEntry>> iteratorConsumer) {
            db.iterate(readTxn, keyRange, iteratorConsumer);
        }

        public Val createValue(final Key key,
                               final StoredValues storedValues,
                               final int index) {
            final Val val;
            if (!dataStore.compiledDepths.getValueIndicesByDepth()[key.getDepth()][index]) {
                val = ValNull.INSTANCE;

            } else {
                final Generator generator = dataStore.compiledColumnArray[index].getGenerator();
                if (key.isGrouped()) {
                    final Supplier<ChildData> childDataSupplier = () -> {
                        // If we don't have any children at the requested depth then return null.
                        if (dataStore.compiledSorters.size() <= key.getChildDepth()) {
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

            // Constrain the absolute limit.
            // In some rare cases we might want to examine more than 1000 rows (see gh-5198).
            final int absoluteLimit = Math.min(limit, MAX_LIST_SIZE);
            final OffsetRange offsetRange = new OffsetRange(0, absoluteLimit);

            final LmdbDataStore dataStore = readContext.dataStore;
            dataStore.getChildren(
                    readContext,
                    key,
                    key.getChildDepth(),
                    absoluteLimit,
                    trimTop,
                    OpenGroups.NONE, // Don't traverse any child rows.
                    readContext.timeFilter,
                    IdentityItemMapper.INSTANCE,
                    offsetRange,
                    fetchState,
                    item -> result.add(((LmdbItem) item).storedValues));
            return result;
        }

        private long countChildren(final Key parentKey,
                                   final int depth) {
            // If we don't have any children at the requested depth then return 0.
            final LmdbDataStore dataStore = readContext.dataStore;
            if (dataStore.compiledSorters.size() <= depth) {
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

    private static class SortedItems {

        private final int minArraySize;
        private final int maxArraySize;
        private final int trimmedSize;
        private final boolean trimTop;
        private final CompiledSorter<Item> sorter;
        private Item[] array;
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
            array = new Item[minArraySize];
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

            // Sort.
            Arrays.sort(array, 0, size, sorter);
        }

        private void trim(final int trimmedSize,
                          final boolean trimTop) {
            final int len = Math.max(minArraySize, trimmedSize);
            final Item[] newArray = new Item[len];
            if (trimTop) {
                System.arraycopy(array, size - trimmedSize, newArray, 0, trimmedSize);
            } else {
                System.arraycopy(array, 0, newArray, 0, trimmedSize);
            }
            array = newArray;
            size = trimmedSize;
        }

        void add(final Item item) {
            if (size == array.length) {
                final Item[] newArray = new Item[Math.min(maxArraySize, size * 2)];
                System.arraycopy(array, 0, newArray, 0, array.length);
                array = newArray;
            }
            array[size++] = item;

            trimmed = false;
            if (size >= maxArraySize) {
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
                public Item next() {
                    return array[pos++];
                }
            };
        }
    }


    // --------------------------------------------------------------------------------


    private static class LmdbItem implements Item {

        private final LmdbReadContext readContext;
        private final Key key;
        private final CompiledColumn[] compiledColumn;
        private final StoredValues storedValues;

        public LmdbItem(final LmdbReadContext readContext,
                        final Key key,
                        final CompiledColumn[] compiledColumn,
                        final StoredValues storedValues) {
            this.readContext = readContext;
            this.key = key;
            this.compiledColumn = compiledColumn;
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

        @Override
        public int size() {
            return compiledColumn.length;
        }

        @Override
        public Val[] toArray() {
            final Val[] vals = new Val[size()];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = getValue(i);
            }
            return vals;
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
            lmdbDataStore.reportRowsWithNoWindowTime();
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
