/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ChildData;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapDataStore implements DataStore, Data {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MapDataStore.class);

    private final Serialisers serialisers;
    private final Map<Key, ItemsImpl> childMap = new ConcurrentHashMap<>();

    private final ValueReferenceIndex valueReferenceIndex;
    private final CompiledField[] compiledFields;
    private final CompiledSorter<ItemImpl>[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();

    private final GroupingFunction[] groupingFunctions;
    private final boolean hasSort;
    private final CompletionState completionState = new CompletionStateImpl();
    private final KeyFactory keyFactory;

    private volatile boolean hasEnoughData;

    public MapDataStore(final Serialisers serialisers,
                        final TableSettings tableSettings,
                        final FieldIndex fieldIndex,
                        final Map<String, String> paramMap,
                        final DataStoreSettings dataStoreSettings) {
        this.serialisers = serialisers;
        final CompiledFields compiledFields = CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);
        valueReferenceIndex = compiledFields.getValueReferenceIndex();
        this.compiledFields = compiledFields.getCompiledFields();
        final CompiledDepths compiledDepths = new CompiledDepths(this.compiledFields, tableSettings.showDetail());
        this.compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), this.compiledFields);
        this.compiledDepths = compiledDepths;
        final KeyFactoryConfig keyFactoryConfig = new BasicKeyFactoryConfig();
        keyFactory = KeyFactoryFactory.create(keyFactoryConfig, compiledDepths);
        this.maxResults = dataStoreSettings.getMaxResults();
        this.storeSize = dataStoreSettings.getStoreSize();

        groupingFunctions = new GroupingFunction[compiledDepths.getMaxDepth() + 1];
        for (int depth = 0; depth <= compiledDepths.getMaxGroupDepth(); depth++) {
            groupingFunctions[depth] = new GroupingFunction(compiledFields.getCompiledFields());
        }

        // Find out if we have any sorting.
        boolean hasSort = false;
        for (final CompiledSorter<ItemImpl> sorter : compiledSorters) {
            if (sorter != null) {
                hasSort = true;
                break;
            }
        }
        this.hasSort = hasSort;
    }

    /**
     * Add some values to the data store.
     *
     * @param values The values to add to the store.
     */
    @Override
    public void add(final Val[] values) {
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

            Val[] groupValues = Val.empty();
            if (groupSize > 0) {
                groupValues = new Val[groupSize];
            }

            int groupIndex = 0;
            for (int fieldIndex = 0; fieldIndex < compiledFields.length; fieldIndex++) {
                final CompiledField compiledField = compiledFields[fieldIndex];

                final Generator generator = compiledField.getGenerator();
                if (generator != null) {
                    final ValCache valCache = new ValCache(generator);
                    Val value;

                    // If this is the first level then check if we should filter out this data.
                    if (depth == 0) {
                        final CompiledFilter compiledFilter = compiledField.getCompiledFilter();
                        if (compiledFilter != null) {
                            // If we are filtering then we need to evaluate this field
                            // now so that we can filter the resultant value.
                            value = valCache.getVal(values, storedValues);

                            if (value != null && !compiledFilter.match(value.toString())) {
                                // We want to exclude this item so get out of this method ASAP.
                                return;
                            }
                        }
                    }

                    // If we are grouping at this level then evaluate the expression and add to the group values.
                    if (groupIndices[fieldIndex]) {
                        // If we haven't already created the generator then do so now.
                        value = valCache.getVal(values, storedValues);
                        groupValues[groupIndex++] = value;
                    }

                    // If we need a value at this level then evaluate the expression and add the value.
                    if (valueIndices[fieldIndex]) {
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
        final Function<Stream<ItemImpl>, Stream<ItemImpl>> sortingFunction = compiledSorters[depth];

        childMap.compute(parentKey, (k, v) -> {
            ItemsImpl result = v;

            if (result == null) {
                result = new ItemsImpl(
                        storeSize.size(depth),
                        this,
                        groupingFunction,
                        sortingFunction,
                        this::remove);
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
                    resultCount.addAndGet(-items.size());
                    items.getIterable().forEach(item -> remove(item.getKey()));
                }
            });
        }
    }

    @Override
    public void getData(final Consumer<Data> consumer) {
        consumer.accept(this);
    }

    /**
     * Get child items from the data for the provided parent key and time filter.
     *
     * @param key        The parent key to get child items for.
     * @param timeFilter The time filter to use to limit the data returned.
     * @return The filtered child items for the parent key.
     */
    @Override
    public ItemsImpl get(final Key key, final TimeFilter timeFilter) {
        if (timeFilter != null) {
            throw new RuntimeException("Time filtering is not supported by the map data store");
        }

        final ItemsImpl result;
        if (key == null) {
            result = childMap.get(Key.ROOT_KEY);
        } else {
            result = childMap.get(key);
        }
        return result;
    }


    private List<ItemImpl> getChildren(final Key parentKey,
                                       final TimeFilter timeFilter,
                                       final int childDepth,
                                       final int trimmedSize,
                                       final boolean trimTop) {
        ItemsImpl items = get(parentKey, timeFilter);
        if (items == null) {
            return null;
        }
        List<ItemImpl> list = items.copy();
        if (list.size() <= trimmedSize) {
            return list;
        }
        if (trimTop) {
            return list.subList(list.size() - trimmedSize, list.size());
        } else {
            return list.subList(0, trimmedSize);
        }
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
     * @return True if we still happy to keep on receiving data, false otherwise.
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
    public Serialisers getSerialisers() {
        return serialisers;
    }

    @Override
    public KeyFactory getKeyFactory() {
        return keyFactory;
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

    public static class ItemsImpl implements Items {

        private final int trimmedSize;
        private final int maxSize;
        private final MapDataStore dataStore;
        private final Function<Stream<ItemImpl>, Stream<ItemImpl>> groupingFunction;
        private final Function<Stream<ItemImpl>, Stream<ItemImpl>> sortingFunction;
        private final Consumer<Key> removeHandler;

        private volatile List<ItemImpl> list;

        private volatile boolean trimmed = true;
        private volatile boolean full;

        ItemsImpl(final int trimmedSize,
                  final MapDataStore dataStore,
                  final Function<Stream<ItemImpl>, Stream<ItemImpl>> groupingFunction,
                  final Function<Stream<ItemImpl>, Stream<ItemImpl>> sortingFunction,
                  final Consumer<Key> removeHandler) {
            this.trimmedSize = trimmedSize;
            if (trimmedSize < Integer.MAX_VALUE / 2) {
                this.maxSize = trimmedSize * 2;
            } else {
                this.maxSize = Integer.MAX_VALUE;
            }
            this.dataStore = dataStore;
            this.groupingFunction = groupingFunction;
            this.sortingFunction = sortingFunction;
            this.removeHandler = removeHandler;
            list = new ArrayList<>();
        }

        synchronized void add(final Key groupKey, final StoredValues storedValues) {
            if (groupingFunction != null || sortingFunction != null) {
                list.add(new ItemImpl(dataStore, groupKey, storedValues));
                trimmed = false;
                if (list.size() > maxSize) {
                    sortAndTrim();
                }
            } else if (list.size() < trimmedSize) {
                list.add(new ItemImpl(dataStore, groupKey, storedValues));
            } else {
                full = true;
                removeHandler.accept(groupKey);
            }
        }

        private synchronized void sortAndTrim() {
            if (!trimmed) {
                // We won't group, sort or trim lists with only a single item obviously.
                if (list.size() > 1) {
                    if (groupingFunction != null || sortingFunction != null) {
                        Stream<ItemImpl> stream = list
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

                    while (list.size() > trimmedSize) {
                        final ItemImpl lastItem = list.remove(list.size() - 1);

                        // Tell the remove handler that we have removed an item.
                        removeHandler.accept(lastItem.getKey());
                    }
                }
                trimmed = true;
            }
        }

        private synchronized List<ItemImpl> copy() {
            sortAndTrim();
            return new ArrayList<>(list);
        }

        @Override
        public Item get(final int index) {
            return copy().get(index);
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public Iterable<StoredValues> getStoredValueIterable() {
            final List<ItemImpl> items = copy();
            return () -> items.stream().map(item -> item.storedValues).iterator();
        }

        @Override
        public Iterable<Item> getIterable() {
            final List<ItemImpl> items = copy();
            return () -> items.stream().map(item -> (Item) item).iterator();
        }
    }

    public static class ItemImpl implements Item {

        private final MapDataStore dataStore;
        private final Key key;
        private final StoredValues storedValues;
        private final Val[] cachedValues;

        public ItemImpl(final MapDataStore dataStore,
                        final Key key,
                        final StoredValues storedValues) {
            this.dataStore = dataStore;
            this.key = key;
            this.storedValues = storedValues;
            this.cachedValues = new Val[dataStore.compiledFields.length];
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
            final Generator generator = dataStore.compiledFields[index].getGenerator();
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
                        final ItemsImpl items = dataStore.get(key, null);
                        if (items != null) {
                            return items.size();
                        }
                        return 0;
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
                        final List<ItemImpl> items = dataStore.getChildren(
                                key,
                                null,
                                key.getChildDepth(),
                                limit,
                                trimTop);
                        if (items != null && items.size() > 0) {
                            return () -> items.stream().map(item -> item.storedValues).iterator();
                        }
                        return Collections::emptyIterator;
                    }
                };
                val = generator.eval(storedValues, childDataSupplier);

            } else {
                val = generator.eval(storedValues, null);
            }
            return val;
        }
    }

    private class GroupingFunction implements Function<Stream<ItemImpl>, Stream<ItemImpl>> {

        private final CompiledField[] compiledFields;

        public GroupingFunction(final CompiledField[] compiledFields) {
            this.compiledFields = compiledFields;
        }

        @Override
        public Stream<ItemImpl> apply(final Stream<ItemImpl> stream) {
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
                        for (int i = 0; i < compiledFields.length; i++) {
                            final Generator generator = compiledFields[i].getGenerator();
                            generator.merge(result, storedValues);
                        }
                    }

                    return result;
                });
            });
            return groupingMap
                    .entrySet()
                    .parallelStream()
                    .map(e -> new ItemImpl(MapDataStore.this, e.getKey(), e.getValue()));
        }
    }
}
