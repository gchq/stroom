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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.ChildData;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapDataStore implements DataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MapDataStore.class);

    private final String componentId;
    private final Map<Key, ItemsImpl> childMap = new ConcurrentHashMap<>();

    private final ValueReferenceIndex valueReferenceIndex;
    private final CompiledColumns compiledColumns;
    private final CompiledColumn[] compiledColumnsArray;
    private final CompiledSorters<MapItem> compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();

    private final GroupingFunction[] groupingFunctions;
    private final boolean hasSort;
    private final CompletionState completionState = new CompletionStateImpl();
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final ResultStoreMapConfig resultStoreMapConfig;
    private final DateTimeSettings dateTimeSettings;

    private volatile boolean hasEnoughData;

    public MapDataStore(final String componentId,
                        final TableSettings tableSettings,
                        final ExpressionContext expressionContext,
                        final FieldIndex fieldIndex,
                        final Map<String, String> paramMap,
                        final DataStoreSettings dataStoreSettings,
                        final ErrorConsumer errorConsumer,
                        final ResultStoreMapConfig resultStoreMapConfig) {
        this.componentId = componentId;
        final List<Column> columns = tableSettings.getColumns();
        this.dateTimeSettings = expressionContext == null
                ? null
                : expressionContext.getDateTimeSettings();
        this.compiledColumns = CompiledColumns.create(expressionContext, columns, fieldIndex, paramMap);
        valueReferenceIndex = compiledColumns.getValueReferenceIndex();
        this.compiledColumnsArray = compiledColumns.getCompiledColumns();
        final CompiledDepths compiledDepths = new CompiledDepths(this.compiledColumnsArray, tableSettings.showDetail());
        this.compiledSorters = new CompiledSorters<>(compiledDepths, columns);
        this.compiledDepths = compiledDepths;
        final KeyFactoryConfig keyFactoryConfig = new BasicKeyFactoryConfig();
        keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        this.maxResults = dataStoreSettings.getMaxResults();
        this.errorConsumer = errorConsumer;
        this.resultStoreMapConfig = resultStoreMapConfig;

        groupingFunctions = new GroupingFunction[compiledDepths.getMaxDepth() + 1];
        for (int depth = 0; depth <= compiledDepths.getMaxGroupDepth(); depth++) {
            groupingFunctions[depth] = new GroupingFunction(compiledColumns.getCompiledColumns());
        }

        // Find out if we have any sorting.
        this.hasSort = compiledSorters.hasSort();
    }

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    @Override
    public void accept(final Val[] values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        Key key = Key.ROOT_KEY;
        Key parentKey = key;
        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final int groupSize = groupSizeByDepth[depth];
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            Val[] groupValues = Val.emptyArray();
            if (groupSize > 0) {
                groupValues = new Val[groupSize];
            }

            int groupIndex = 0;
            for (int columnIndex = 0; columnIndex < compiledColumnsArray.length; columnIndex++) {
                final CompiledColumn compiledColumn = compiledColumnsArray[columnIndex];

                final Generator generator = compiledColumn.getGenerator();
                if (generator != null) {
                    final ValCache valCache = new ValCache(generator);
                    Val value;

                    // ALL VALUE FILTERING IS DONE IN LMDB SO NOT NEEDED HERE.
//                    // If this is the first level then check if we should filter out this data.
//                    if (depth == 0) {
//                        final Optional<Predicate<String>> optionalCompiledFilter = compiledColumn.getCompiledFilter();
//                        if (optionalCompiledFilter.isPresent()) {
//                            // If we are filtering then we need to evaluate this field
//                            // now so that we can filter the resultant value.
//                            value = valCache.getVal(values, storedValues);
//
//                            if (value != null && !optionalCompiledFilter.get().test(value.toString())) {
//                                // We want to exclude this item so get out of this method ASAP.
//                                return;
//                            }
//                        }
//                    }

                    // If we are grouping at this level then evaluate the expression and add to the group values.
                    if (groupIndices[columnIndex]) {
                        // If we haven't already created the generator then do so now.
                        value = valCache.getVal(values, storedValues);
                        groupValues[groupIndex++] = value;
                    }

                    // If we need a value at this level then evaluate the expression and add the value.
                    if (valueIndices[columnIndex]) {
                        // If we haven't already created the generator then do so now.
                        value = valCache.getVal(values, storedValues);
                    }
                }
            }

            // Trim group values.
            if (groupIndex < groupSize) {
                groupValues = Arrays.copyOf(groupValues, groupIndex);
            }

            if (depth <= compiledDepths.getMaxGroupDepth()) {
                // This is a grouped item.
                key = key.resolve(0, groupValues);

            } else {
                // This item will not be grouped.
                key = key.resolve(0, keyFactory.getUniqueId());
            }

            addToChildMap(depth, parentKey, key, storedValues);
            parentKey = key;
        }
    }

    private void addToChildMap(final int depth,
                               final Key parentKey,
                               final Key groupKey,
                               final StoredValues storedValues) {
        LOGGER.trace(() -> "addToChildMap called for item");
        if (Thread.currentThread().isInterrupted() || hasEnoughData) {
            completionState.signalComplete();
        }

        // Update the total number of results that we have received.
        totalResultCount.getAndIncrement();

        final GroupingFunction groupingFunction = groupingFunctions[depth];
        final Function<Stream<MapItem>, Stream<MapItem>> sortingFunction = compiledSorters.get(depth);

        childMap.compute(parentKey, (k, v) -> {
            ItemsImpl result = v;

            if (result == null) {
                result = new ItemsImpl(
                        depth,
                        maxResults.size(depth),
                        this,
                        groupingFunction,
                        sortingFunction,
                        this::remove,
                        resultStoreMapConfig);
                result.add(groupKey, storedValues);
                resultCount.incrementAndGet();

            } else {
                result.add(groupKey, storedValues);
            }

            return result;
        });

        // Some searches can be terminated early if the user is not sorting or grouping.
        if (!hasEnoughData && !hasSort && !compiledDepths.hasGroup()) {
            // No sorting or grouping so we can stop the search as soon as we have the number of results requested by
            // the client
            if (maxResults != null && totalResultCount.get() >= maxResults.size(0)) {
                hasEnoughData = true;
            }
        }

        LOGGER.trace(() -> "Finished adding items to the queue");
    }

    private void remove(final Key parentKey) {
        if (parentKey != null) {
            // Execute removal asynchronously to prevent blocking.
            CompletableFuture.runAsync(() -> {
                final ItemsImpl items = childMap.remove(parentKey);
                if (items != null) {
                    resultCount.addAndGet(-items.list.size());
                    items.list.forEach(item -> remove(item.getKey()));
                }
            });
        }
    }

    @Override
    public List<Column> getColumns() {
        return compiledColumns.getColumns();
    }

    @Override
    public void fetch(final List<Column> columns,
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
                .orElse(OffsetRange.UNBOUNDED);

        final FetchState fetchState = new FetchState();
        fetchState.countRows = totalRowCountConsumer != null;
        fetchState.reachedRowLimit = fetchState.length >= enforcedRange.getLength();
        fetchState.keepGoing = fetchState.justCount || !fetchState.reachedRowLimit;

        getChildren(
                Key.ROOT_KEY,
                openGroups,
                mapper,
                enforcedRange,
                fetchState,
                resultConsumer);

        if (totalRowCountConsumer != null) {
            totalRowCountConsumer.accept(fetchState.totalRowCount);
        }
    }

    private void getChildren(final Key parentKey,
                             final OpenGroups openGroups,
                             final ItemMapper mapper,
                             final OffsetRange range,
                             final FetchState fetchState,
                             final Consumer<Item> resultConsumer) {
        final ItemsImpl items = childMap.get(parentKey);
        if (items == null) {
            return;
        }
        final List<MapItem> list = items.copy();

        // Transfer the sorted items to the result.
        for (final Item item : list) {
            if (!fetchState.reachedRowLimit && range.getOffset() <= fetchState.offset) {
                mapper.create(item).forEach(row -> {
                    fetchState.totalRowCount++;
                    if (!fetchState.reachedRowLimit) {
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
                        fetchState.offset++;
                    }
                });
            } else {
                fetchState.totalRowCount++;
            }

            // Add children if the group is open.
            if (fetchState.keepGoing && openGroups.isOpen(item.getKey())) {
                getChildren(item.getKey(),
                        openGroups,
                        mapper,
                        range,
                        fetchState,
                        resultConsumer);
            }
        }
    }

    private long countChildren(final Key parentKey) {
        final ItemsImpl items = childMap.get(parentKey);
        if (items == null) {
            return 0;
        }
        final List<MapItem> list = items.copy();
        // FIXME : NOTE THIS COUNT IS NOT FILTERED BY THE MAPPER.
        return list.size();
    }

    /**
     * Clear the data store.
     */
    @Override
    public void clear() {
        LOGGER.trace(() -> "clear()", new RuntimeException("clear"));
        totalResultCount.set(0);
        childMap.clear();
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
        throw new RuntimeException("Not implemented");
//        return Metrics.measure("readPayload", () -> {
//            final int count = input.readInt();
//            for (int i = 0; i < count; i++) {
//                final int length = input.readInt();
//                final byte[] bytes = input.readBytes(length);
//
//                final RawItem rawItem = itemSerialiser.readRawItem(bytes);
//                final byte[] keyBytes = rawItem.getKey();
//                final Key key = itemSerialiser.toKey(keyBytes);
//
//                KeyPart lastPart = key.getLast();
//                if (lastPart != null && !lastPart.isGrouped()) {
//                    // Ensure sequence numbers are unique for this data store.
//                    ((UngroupedKeyPart) lastPart).setSequenceNumber(ungroupedItemSequenceNumber.incrementAndGet());
//                }
//
//                final Key parent = key.getParent();
//                final byte[] parentKeyBytes = itemSerialiser.toBytes(parent);
//
//                final byte[] generators = rawItem.getGenerators();
//
//                addToChildMap(key.getDepth(), parentKeyBytes, keyBytes, generators);
//            }
//
//            // Return success if we have not been asked to terminate and we are still willing to accept data.
//            return !Thread.currentThread().isInterrupted() && !hasEnoughData;
//        });
    }

    /**
     * Write data from the data store to an output removing them from the datastore as we go as they will be transferred
     * to another store.
     *
     * @param output The output to write to.
     */
    @Override
    public void writePayload(final Output output) {
        throw new RuntimeException("Not implemented");
//        Metrics.measure("writePayload", () -> {
//            final List<byte[]> list = new ArrayList<>();
//            childMap.keySet().forEach(groupKey -> {
//                final ItemsImpl items = childMap.remove(groupKey);
//                if (items != null) {
//                    list.addAll(items.getList());
//                }
//            });
//
//            output.writeInt(list.size());
//            for (final byte[] item : list) {
//                output.writeInt(item.length);
//                output.writeBytes(item);
//            }
//        });
    }

    @Override
    public long getByteSize() {
        long size = 0;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(childMap);
            }
            size = baos.size();
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return size;
    }

    @Override
    public KeyFactory getKeyFactory() {
        return keyFactory;
    }

    @Override
    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    private static class ValCache {

        private final Generator generator;
        private Val val;

        public ValCache(final Generator generator) {
            this.generator = generator;
        }

        Val getVal(final Val[] values, final StoredValues storedValues) {
            if (val == null) {
                generator.set(values, storedValues);

                // If we are filtering then we need to evaluate this field
                // now so that we can filter the resultant value.
                val = generator.eval(storedValues, null);
            }
            return val;
        }
    }

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

    public static class ItemsImpl {

        private final int depth;
        private final int trimmedSize;
        private final int maxSize;
        private final MapDataStore dataStore;
        private final Function<Stream<MapItem>, Stream<MapItem>> groupingFunction;
        private final Function<Stream<MapItem>, Stream<MapItem>> sortingFunction;
        private final Consumer<Key> removeHandler;

        private volatile List<MapItem> list;

        private volatile boolean trimmed = true;

        ItemsImpl(final int depth,
                  final long limit,
                  final MapDataStore dataStore,
                  final Function<Stream<MapItem>, Stream<MapItem>> groupingFunction,
                  final Function<Stream<MapItem>, Stream<MapItem>> sortingFunction,
                  final Consumer<Key> removeHandler,
                  final ResultStoreMapConfig resultStoreMapConfig) {
            this.depth = depth;
            this.trimmedSize = (int) Math.max(Math.min(limit, resultStoreMapConfig.getTrimmedSizeLimit()), 0);
            this.maxSize = Math.max(this.trimmedSize * 2, resultStoreMapConfig.getMinUntrimmedSize());
            this.dataStore = dataStore;
            this.groupingFunction = groupingFunction;
            this.sortingFunction = sortingFunction;
            this.removeHandler = removeHandler;
            list = new ArrayList<>();
        }

        synchronized void add(final Key groupKey, final StoredValues storedValues) {
            if (groupingFunction != null || sortingFunction != null) {
                list.add(new MapItem(dataStore, groupKey, dataStore.compiledColumnsArray, storedValues));
                trimmed = false;
                if (list.size() > maxSize) {
                    sortAndTrim();
                }
            } else if (list.size() < trimmedSize) {
                list.add(new MapItem(dataStore, groupKey, dataStore.compiledColumnsArray, storedValues));
            } else {
                logTruncation();
                removeHandler.accept(groupKey);
            }
        }

        private synchronized void sortAndTrim() {
            if (!trimmed) {
                // We won't group, sort or trim lists with only a single item obviously.
                if (list.size() > 1) {
                    if (groupingFunction != null || sortingFunction != null) {
                        Stream<MapItem> stream = list
                                .parallelStream();

                        // Group items.
                        if (groupingFunction != null) {
                            stream = groupingFunction.apply(stream);
                        }

                        // Sort the list before trimming if we have a comparator.
                        if (sortingFunction != null) {
                            stream = sortingFunction.apply(stream);
                        }

                        list = stream
                                .collect(Collectors.toList());
                    }

                    if (list.size() > trimmedSize) {
                        logTruncation();
                        while (list.size() > trimmedSize) {
                            final MapItem lastItem = list.removeLast();

                            // Tell the remove handler that we have removed an item.
                            removeHandler.accept(lastItem.getKey());
                        }
                    }
                }
                trimmed = true;
            }
        }

        private void logTruncation() {
            dataStore.errorConsumer.add(Severity.WARNING, () ->
                    "Truncating data for vis '" +
                    dataStore.componentId +
                    "' to " +
                    trimmedSize +
                    " data points at depth " +
                    depth);
        }

        private synchronized List<MapItem> copy() {
            sortAndTrim();
            return new ArrayList<>(list);
        }
    }

    public static class MapItem implements Item {

        private final MapDataStore dataStore;
        private final Key key;
        private final CompiledColumn[] compiledColumns;
        private final StoredValues storedValues;

        public MapItem(final MapDataStore dataStore,
                       final Key key,
                       final CompiledColumn[] compiledColumns,
                       final StoredValues storedValues) {
            this.dataStore = dataStore;
            this.key = key;
            this.compiledColumns = compiledColumns;
            this.storedValues = storedValues;
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index) {
            return createValue(dataStore, key, storedValues, index);
        }

        @Override
        public int size() {
            return compiledColumns.length;
        }

        @Override
        public Val[] toArray() {
            final Val[] vals = new Val[size()];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = getValue(i);
            }
            return vals;
        }

        private static Val createValue(final MapDataStore dataStore,
                                       final Key key,
                                       final StoredValues storedValues,
                                       final int index) {
            final Val val;
            final Generator generator = dataStore.compiledColumnsArray[index].getGenerator();
            if (key.isGrouped()) {
                final Supplier<ChildData> childDataSupplier = () -> new ChildData() {
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
                        return dataStore.countChildren(key);
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

                        dataStore.getChildren(
                                key,
                                OpenGroups.NONE, // Don't traverse any child rows.
                                IdentityItemMapper.INSTANCE,
                                OffsetRange.ZERO_1000, // Max 1000 child items.
                                fetchState,
                                item -> result.add(((MapItem) item).storedValues));
                        if (result.size() > limit) {
                            if (trimTop) {
                                return result.subList(result.size() - limit, result.size());
                            } else {
                                return result.subList(0, limit);
                            }
                        }
                        return result;
                    }
                };
                val = generator.eval(storedValues, childDataSupplier);

            } else {
                val = generator.eval(storedValues, null);
            }
            return val;
        }
    }

    private class GroupingFunction implements Function<Stream<MapItem>, Stream<MapItem>> {

        private final CompiledColumn[] compiledColumns;

        public GroupingFunction(final CompiledColumn[] compiledColumns) {
            this.compiledColumns = compiledColumns;
        }

        @Override
        public Stream<MapItem> apply(final Stream<MapItem> stream) {
            final Map<Key, StoredValues> groupingMap = new ConcurrentHashMap<>();
            stream.forEach(item -> {
                final Key rawKey = item.getKey();
                final StoredValues storedValues = item.storedValues;

                groupingMap.compute(rawKey, (k, v) -> {
                    StoredValues result = v;

                    if (result == null) {
                        result = storedValues;
                    } else {
                        // Combine the new item into the original item.
                        for (final CompiledColumn compiledColumn : compiledColumns) {
                            final Generator generator = compiledColumn.getGenerator();
                            generator.merge(result, storedValues);
                        }
                    }

                    return result;
                });
            });

            return groupingMap
                    .entrySet()
                    .parallelStream()
                    .map(e -> new MapItem(MapDataStore.this, e.getKey(), compiledColumns, e.getValue()));
        }
    }
}
