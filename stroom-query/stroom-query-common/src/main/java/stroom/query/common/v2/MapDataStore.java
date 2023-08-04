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
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final List<Field> fields;
    private final CompiledFields compiledFields;
    private final CompiledField[] compiledFieldsArray;
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
        fields = tableSettings.getFields();
        this.compiledFields = CompiledFields.create(fields, fieldIndex, paramMap);
        valueReferenceIndex = compiledFields.getValueReferenceIndex();
        this.compiledFieldsArray = compiledFields.getCompiledFields();
        final CompiledDepths compiledDepths = new CompiledDepths(this.compiledFieldsArray, tableSettings.showDetail());
        this.compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), this.compiledFieldsArray);
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
            for (int fieldIndex = 0; fieldIndex < compiledFieldsArray.length; fieldIndex++) {
                final CompiledField compiledField = compiledFieldsArray[fieldIndex];

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
                    resultCount.addAndGet(-items.list.size());
                    items.list.forEach(item -> remove(item.getKey()));
                }
            });
        }
    }

    @Override
    public List<Field> getFields() {
        return compiledFields.getFields();
    }

    @Override
    public void getData(final Consumer<Data> consumer) {
        consumer.accept(this);
    }

    @Override
    public <R> Items<R> get(final OffsetRange rng,
                            final Set<Key> openGroups,
                            final TimeFilter timeFilter,
                            final ItemMapper<R> mapper) {
        final OffsetRange range = Optional
                .ofNullable(rng)
                .orElse(OffsetRange.ZERO_100);
        return new Items<>() {
            private long totalRowCount = -1;

            @Override
            public long totalRowCount() {
                if (this.totalRowCount == -1) {
                    long totalRowCount = 0;

                    if (openGroups == null) {
                        for (final ItemsImpl items : childMap.values()) {
                            final List<ItemImpl> list = items.copy();
                            if (mapper.hidesRows()) {
                                for (final ItemImpl item : list) {
                                    final R row = mapper.create(fields, item);
                                    if (row != null) {
                                        totalRowCount++;
                                    }
                                }
                            } else {
                                totalRowCount += list.size();
                            }
                        }
                    } else {

                        // Calculate complete size of all requested data.
                        final Set<Key> groups = new HashSet<>();
                        groups.add(Key.ROOT_KEY);
                        groups.addAll(openGroups);

                        for (final Key parentKey : groups) {
                            final ItemsImpl items = childMap.get(parentKey);
                            if (items != null) {
                                final List<ItemImpl> list = items.copy();
                                if (mapper.hidesRows()) {
                                    for (final ItemImpl item : list) {
                                        final R row = mapper.create(fields, item);
                                        if (row != null) {
                                            totalRowCount++;
                                        }
                                    }
                                } else {
                                    totalRowCount += list.size();
                                }
                            }
                        }
                    }

                    this.totalRowCount = totalRowCount;
                }
                return this.totalRowCount;
            }

            @Override
            public void fetch(final Consumer<R> resultConsumer) {
                final AtomicLong position = new AtomicLong();
                final AtomicLong length = new AtomicLong();
                getChildren(
                        Key.ROOT_KEY,
                        openGroups,
                        mapper,
                        range,
                        position,
                        length,
                        resultConsumer);
            }
        };
    }

    private <R> void getChildren(final Key parentKey,
                                 final Set<Key> openGroups,
                                 final ItemMapper<R> mapper,
                                 final OffsetRange range,
                                 final AtomicLong offset,
                                 final AtomicLong length,
                                 final Consumer<R> resultConsumer) {
        final ItemsImpl items = childMap.get(parentKey);
        if (items == null) {
            return;
        }
        final List<ItemImpl> list = items.copy();
        for (final ItemImpl item : list) {
            final R row = mapper.create(fields, item);
            if (row != null) {
                if (range.getOffset() <= offset.get()) {
                    resultConsumer.accept(row);
                    final long len = length.incrementAndGet();
                    if (len >= range.getLength()) {
                        return;
                    }
                }
                offset.incrementAndGet();

                // Add children if the group is open.
                final Key key = item.key;
                if (openGroups == null || openGroups.contains(key)) {
                    getChildren(key,
                            openGroups,
                            mapper,
                            range,
                            offset,
                            length,
                            resultConsumer);
                }
            }
        }
    }

    private long countChildren(final Key parentKey) {
        final ItemsImpl items = childMap.get(parentKey);
        if (items == null) {
            return 0;
        }
        final List<ItemImpl> list = items.copy();
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

    public static class ItemsImpl {

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

            // FIXME : THIS IS HARD CODED TO REDUCE CHANCE OF OOME.
            this.trimmedSize = Math.min(trimmedSize, 100_000); // Don't ever allow more than 100K data points.

            int maxSize = this.trimmedSize * 2;
            // FIXME : SEE ABOVE NOTE ABOUT HARD CODING.
            maxSize = Math.min(maxSize, 200_000);
            this.maxSize = Math.max(maxSize, 1_000);

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
    }

    public static class ItemImpl implements Item {

        private final MapDataStore dataStore;
        private final Key key;
        private final StoredValues storedValues;

        public ItemImpl(final MapDataStore dataStore,
                        final Key key,
                        final StoredValues storedValues) {
            this.dataStore = dataStore;
            this.key = key;
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

        private static Val createValue(final MapDataStore dataStore,
                                       final Key key,
                                       final StoredValues storedValues,
                                       final int index) {
            Val val;
            final Generator generator = dataStore.compiledFieldsArray[index].getGenerator();
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
                        dataStore.getChildren(
                                key,
                                Collections.emptySet(),
                                IdentityItemMapper.INSTANCE,
                                OffsetRange.ZERO_1000, // Max 1000 child items.
                                new AtomicLong(),
                                new AtomicLong(),
                                item -> result.add(((ItemImpl) item).storedValues));
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
                        for (final CompiledField compiledField : compiledFields) {
                            final Generator generator = compiledField.getGenerator();
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
