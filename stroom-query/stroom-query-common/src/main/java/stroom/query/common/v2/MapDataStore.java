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
import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.FieldOffset;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.Values;
import stroom.query.api.v2.TableSettings;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
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
import javax.validation.constraints.NotNull;

public class MapDataStore implements DataStore, Data {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MapDataStore.class);

    private final Key rootKey;
    private final Map<Key, ItemsImpl> childMap = new ConcurrentHashMap<>();
    private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();

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

    private volatile boolean hasEnoughData;

    public MapDataStore(final Serialisers serialisers,
                        final TableSettings tableSettings,
                        final FieldIndex fieldIndex,
                        final Map<String, String> paramMap,
                        final Sizes maxResults,
                        final Sizes storeSize,
                        final ErrorConsumer errorConsumer) {
        compiledFields = CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFields, tableSettings.showDetail());
        this.compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), compiledFields);
        this.compiledDepths = compiledDepths;
        this.maxResults = maxResults;
        this.storeSize = storeSize;

        rootKey = Key.createRoot(serialisers);
        groupingFunctions = new GroupingFunction[compiledDepths.getMaxDepth() + 1];
        for (int depth = 0; depth <= compiledDepths.getMaxGroupDepth(); depth++) {
            groupingFunctions[depth] = new GroupingFunction();
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
    public void add(final Values values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        Key key = rootKey;
        Key parentKey = key;
        for (int depth = 0; depth < groupIndicesByDepth.length; depth++) {
            final Generator[] generators = new Generator[compiledFields.length];

            final int groupSize = groupSizeByDepth[depth];
            final boolean[] groupIndices = groupIndicesByDepth[depth];
            final boolean[] valueIndices = valueIndicesByDepth[depth];

            Val[] groupValues = ValSerialiser.EMPTY_VALUES;
            if (groupSize > 0) {
                groupValues = new Val[groupSize];
            }

            int groupIndex = 0;
            for (int fieldIndex = 0; fieldIndex < compiledFields.length; fieldIndex++) {
                final CompiledField compiledField = compiledFields[fieldIndex];

                final Expression expression = compiledField.getExpression();
                if (expression != null) {
                    Generator generator = null;
                    Val value = null;

                    // If this is the first level then check if we should filter out this data.
                    if (depth == 0) {
                        final CompiledFilter compiledFilter = compiledField.getCompiledFilter();
                        if (compiledFilter != null) {
                            generator = expression.createGenerator();
                            generator.set(values);

                            // If we are filtering then we need to evaluate this field
                            // now so that we can filter the resultant value.
                            value = generator.eval(null);

                            if (value != null && !compiledFilter.match(value.toString())) {
                                // We want to exclude this item so get out of this method ASAP.
                                return;
                            }
                        }
                    }

                    // If we are grouping at this level then evaluate the expression and add to the group values.
                    if (groupIndices[fieldIndex]) {
                        // If we haven't already created the generator then do so now.
                        if (generator == null) {
                            generator = expression.createGenerator();
                            generator.set(values);
                            value = generator.eval(null);
                        }
                        groupValues[groupIndex++] = value;
                    }

                    // If we need a value at this level then evaluate the expression and add the value.
                    if (valueIndices[fieldIndex]) {
                        // If we haven't already created the generator then do so now.
                        if (generator == null) {
                            generator = expression.createGenerator();
                            generator.set(values);
                        }
                        generators[fieldIndex] = generator;
                    }
                }
            }

            // Trim group values.
            if (groupIndex < groupSize) {
                groupValues = Arrays.copyOf(groupValues, groupIndex);
            }

            if (depth <= compiledDepths.getMaxGroupDepth()) {
                // This is a grouped item.
                key = key.resolve(groupValues);

            } else {
                // This item will not be grouped.
                key = key.resolve(ungroupedItemSequenceNumber.incrementAndGet());
            }

            addToChildMap(depth, parentKey, key, generators);
            parentKey = key;
        }
    }

    private void addToChildMap(final int depth,
                               final Key parentKey,
                               final Key groupKey,
                               final Generator[] generators) {
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
                result.add(groupKey, generators);
                resultCount.incrementAndGet();

            } else {
                result.add(groupKey, generators);
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
                    items.forEach(item -> remove(item.getKey()));
                }
            });
        }
    }

    @Override
    public void getData(final Consumer<Data> consumer) {
        consumer.accept(this);
    }

    /**
     * Get root items from the data store.
     *
     * @return Root items.
     */
    @Override
    public Items get() {
        return get(rootKey);
    }

    /**
     * Get child items from the data store for the provided parent key.
     *
     * @param parentKey The parent key to get child items for.
     * @return The child items for the parent key.
     */
    @Override
    public Items get(final Key parentKey) {
        Items result;

        if (parentKey == null) {
            result = childMap.get(rootKey);
        } else {
            result = childMap.get(parentKey);
        }

        if (result == null) {
            result = new Items() {
                @Override
                public Item get(final int index) {
                    return null;
                }

                @Override
                public int size() {
                    return 0;
                }

                @Override
                @NotNull
                public Iterator<Item> iterator() {
                    return Collections.emptyIterator();
                }
            };
        }

        return result;
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

        synchronized void add(final Key groupKey, final Generator[] generators) {
            if (groupingFunction != null || sortingFunction != null) {
                list.add(new ItemImpl(dataStore, groupKey, generators));
                trimmed = false;
                if (list.size() > maxSize) {
                    sortAndTrim();
                }
            } else if (list.size() < trimmedSize) {
                list.add(new ItemImpl(dataStore, groupKey, generators));
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

        private synchronized List<Item> copy() {
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
        @NotNull
        public Iterator<Item> iterator() {
            return copy().iterator();
        }
    }

    public static class ItemImpl implements Item {

        private final MapDataStore dataStore;
        private final Key key;
        private final Generator[] generators;

        public ItemImpl(final MapDataStore dataStore,
                        final Key key,
                        final Generator[] generators) {
            this.dataStore = dataStore;
            this.key = key;
            this.generators = generators;
        }

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public Val getValue(final int index, final boolean evaluateChildren) {
            Val val = null;

            if (index >= 0 && index < generators.length) {
                final Generator generator = generators[index];
                if (generator != null) {
                    if (evaluateChildren) {
                        final Supplier<ChildData> childDataSupplier = () -> new ChildData() {
                            @Override
                            public Val first() {
                                final Items items = dataStore.get(key);
                                if (items != null && items.size() > 0) {
                                    return items.get(0).getValue(index, false);
                                }
                                return ValNull.INSTANCE;
                            }

                            @Override
                            public Val last() {
                                final Items items = dataStore.get(key);
                                if (items != null && items.size() > 0) {
                                    return items.get(items.size() - 1).getValue(index, false);
                                }
                                return ValNull.INSTANCE;
                            }

                            @Override
                            public Val nth(final int pos) {
                                final Items items = dataStore.get(key);
                                if (items != null && items.size() > pos) {
                                    return items.get(pos).getValue(index, false);
                                }
                                return ValNull.INSTANCE;
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
                                final Items items = dataStore.get(key);
                                if (items != null) {
                                    return ValLong.create(items.size());
                                }
                                return ValNull.INSTANCE;
                            }

                            private Val join(final String delimiter, final int limit, final boolean trimTop) {
                                final Items items = dataStore.get(key);
                                if (items != null && items.size() > 0) {

                                    int start;
                                    int end;
                                    if (trimTop) {
                                        end = items.size() - 1;
                                        start = Math.max(0, end - limit);
                                    } else {
                                        end = Math.min(limit, items.size());
                                        start = 0;
                                    }

                                    final StringBuilder sb = new StringBuilder();
                                    for (int i = start; i <= end; i++) {
                                        final Val val = items.get(i).getValue(index, false);
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
                        val = generator.eval(childDataSupplier);

                    } else {
                        val = generator.eval(null);
                    }
                }
            }
            return val;
        }
    }

    private class GroupingFunction implements Function<Stream<ItemImpl>, Stream<ItemImpl>> {

        @Override
        public Stream<ItemImpl> apply(final Stream<ItemImpl> stream) {
            final Map<Key, Generator[]> groupingMap = new ConcurrentHashMap<>();
            stream.forEach(item -> {
                final Key rawKey = item.getKey();
                final Generator[] generators = item.generators;

                groupingMap.compute(rawKey, (k, v) -> {
                    Generator[] result = v;

                    if (result == null) {
                        result = generators;
                    } else {
                        // Combine the new item into the original item.
                        for (int i = 0; i < result.length; i++) {
                            Generator existingGenerator = result[i];
                            Generator newGenerator = generators[i];
                            if (newGenerator != null) {
                                if (existingGenerator == null) {
                                    result[i] = newGenerator;
                                } else {
                                    existingGenerator.merge(newGenerator);
                                }
                            }
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
