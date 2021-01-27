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

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Input;
import stroom.dashboard.expression.v1.Output;
import stroom.dashboard.expression.v1.OutputFactory;
import stroom.dashboard.expression.v1.Selection;
import stroom.dashboard.expression.v1.Selector;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.query.api.v2.TableSettings;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapDataStore implements DataStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MapDataStore.class);

    private static RawKey ROOT_KEY;

    private final Map<RawKey, ItemsImpl> childMap = new ConcurrentHashMap<>();
    private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();

    private final CompiledField[] compiledFields;
    private final CompiledSorter<UnpackedItem>[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final ItemSerialiser itemSerialiser;

    private final GroupingFunction[] groupingFunctions;
    private final boolean hasSort;
    private final CompletionState completionState = new CompletionStateImpl();

    private volatile boolean hasEnoughData;

    public MapDataStore(final TableSettings tableSettings,
                        final FieldIndex fieldIndex,
                        final Map<String, String> paramMap,
                        final Sizes maxResults,
                        final Sizes storeSize,
                        final OutputFactory outputFactory) {
        compiledFields = CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFields, tableSettings.showDetail());
        this.compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), compiledFields);
        this.compiledDepths = compiledDepths;
        this.maxResults = maxResults;
        this.storeSize = storeSize;

        itemSerialiser = new ItemSerialiser(compiledFields, outputFactory);
        if (ROOT_KEY == null) {
            ROOT_KEY = itemSerialiser.toRawKey(Key.root());
        }

        groupingFunctions = new GroupingFunction[compiledDepths.getMaxDepth() + 1];
        for (int depth = 0; depth <= compiledDepths.getMaxGroupDepth(); depth++) {
            groupingFunctions[depth] = new GroupingFunction(itemSerialiser);
        }

        // Find out if we have any sorting.
        boolean hasSort = false;
        for (final CompiledSorter<UnpackedItem> sorter : compiledSorters) {
            if (sorter != null) {
                hasSort = true;
                break;
            }
        }
        this.hasSort = hasSort;
    }

    @Override
    public void clear() {
        totalResultCount.set(0);
        childMap.clear();
    }

    @Override
    public boolean readPayload(final Input input) {
        return Metrics.measure("readPayload", () -> {
            final int count = input.readInt();
            for (int i = 0; i < count; i++) {
                final int length = input.readInt();
                final byte[] bytes = input.readBytes(length);

                final RawItem rawItem = itemSerialiser.readRawItem(bytes);
                final byte[] keyBytes = rawItem.getKey();
                final Key key = itemSerialiser.toKey(keyBytes);

                KeyPart lastPart = key.getLast();
                if (lastPart != null && !lastPart.isGrouped()) {
                    // Ensure sequence numbers are unique for this data store.
                    ((UngroupedKeyPart) lastPart).setSequenceNumber(ungroupedItemSequenceNumber.incrementAndGet());
                }

                final Key parent = key.getParent();
                final byte[] parentKeyBytes = itemSerialiser.toBytes(parent);

                final byte[] generators = rawItem.getGenerators();

                addToChildMap(key.getDepth(), parentKeyBytes, keyBytes, generators);
            }

            // Return success if we have not been asked to terminate and we are still willing to accept data.
            return !Thread.currentThread().isInterrupted() && !hasEnoughData;
        });
    }

    @Override
    public void writePayload(final Output output) {
        Metrics.measure("writePayload", () -> {
            final List<byte[]> list = new ArrayList<>();
            childMap.keySet().forEach(groupKey -> {
                final ItemsImpl items = childMap.remove(groupKey);
                if (items != null) {
                    list.addAll(items.getList());
                }
            });

            output.writeInt(list.size());
            for (final byte[] item : list) {
                output.writeInt(item.length);
                output.writeBytes(item);
            }
        });
    }

    @Override
    public void add(final Val[] values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        Key key = Key.root();

        byte[] parentKey = ROOT_KEY.getBytes();
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
                    if (groupIndices[fieldIndex] || valueIndices[fieldIndex]) {
                        final Generator generator = expression.createGenerator();
                        generator.set(values);

                        if (groupIndices[fieldIndex]) {
                            groupValues[groupIndex++] = generator.eval();
                        }

                        if (valueIndices[fieldIndex]) {
                            generators[fieldIndex] = generator;
                        }
                    }
                }
            }

            // Trim group values.
            if (groupIndex < groupSize) {
                groupValues = Arrays.copyOf(groupValues, groupIndex);
            }

            KeyPart keyPart;
            if (depth <= compiledDepths.getMaxGroupDepth()) {
                // This is a grouped item.
                keyPart = new GroupKeyPart(groupValues);

            } else {
                // This item will not be grouped.
                keyPart = new UngroupedKeyPart(ungroupedItemSequenceNumber.incrementAndGet());
            }

            key = key.resolve(keyPart);
            final byte[] childKey = itemSerialiser.toBytes(key);
            final byte[] generatorBytes = itemSerialiser.toBytes(generators);

            addToChildMap(depth, parentKey, childKey, generatorBytes);

            parentKey = childKey;
        }
    }

//    public void write(final Val[] values,
//                      final Output output) {
//        for (final CompiledField compiledField : compiledFields) {
//            final Expression expression = compiledField.getExpression();
//            if (expression != null) {
//                final Generator generator = expression.createGenerator();
//                generator.set(values);
//                generator.write(output);
//            }
//        }
//    }
//
//    public Generator[] read(final Input input) {
//        // Process list into fields.
//        final Generator[] generators = new Generator[compiledFields.length];
//        for (int i = 0; i < compiledFields.length; i++) {
//            final CompiledField compiledField = compiledFields[i];
//            final Expression expression = compiledField.getExpression();
//            if (expression != null) {
//                final Generator generator = expression.createGenerator();
//                generator.read(input);
//                generators[i] = generator;
//            }
//        }
//        return generators;
//    }

    private void addToChildMap(final int depth,
                               final byte[] parentKey,
                               final byte[] groupKey,
                               final byte[] generators) {
        LOGGER.trace(() -> "addToChildMap called for item");
        if (Thread.currentThread().isInterrupted() || hasEnoughData) {
            return;
        }

        // Update the total number of results that we have received.
        totalResultCount.getAndIncrement();

        final GroupingFunction groupingFunction = groupingFunctions[depth];
        final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> sortingFunction = compiledSorters[depth];

        childMap.compute(new RawKey(parentKey), (k, v) -> {
            ItemsImpl result = v;

            if (result == null) {
                result = new ItemsImpl(
                        storeSize.size(depth),
                        itemSerialiser,
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

    private void remove(final RawKey parentKey) {
        if (parentKey != null) {
            // Execute removal asynchronously to prevent blocking.
            CompletableFuture.runAsync(() -> {
                final ItemsImpl items = childMap.remove(parentKey);
                if (items != null) {
                    resultCount.addAndGet(-items.size());
                    items.forEach(item -> remove(item.getRawKey()));
                }
            });
        }
    }

    @Override
    public Items get() {
        return get(ROOT_KEY);
    }

    @Override
    public long getSize() {
        return resultCount.get();
    }

    @Override
    public long getTotalSize() {
        return totalResultCount.get();
    }

    @Override
    public Items get(final RawKey rawKey) {
        Items result;

        if (rawKey == null) {
            result = childMap.get(ROOT_KEY);
        } else {
            result = childMap.get(rawKey);
        }

        if (result == null) {
            result = new Items() {
                @Override
                public int size() {
                    return 0;
                }

                @Override
                @Nonnull
                public Iterator<Item> iterator() {
                    return Collections.emptyIterator();
                }
            };
        }

        return result;
    }

    public static class ItemsImpl implements Items {
        private final int trimmedSize;
        private final int maxSize;
        private final ItemSerialiser itemSerialiser;
        private final MapDataStore dataStore;
        private final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> groupingFunction;
        private final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> sortingFunction;
        private final Consumer<RawKey> removeHandler;

        private volatile List<byte[]> list;

        private volatile boolean trimmed = true;
        private volatile boolean full;

        ItemsImpl(final int trimmedSize,
                  final ItemSerialiser itemSerialiser,
                  final MapDataStore dataStore,
                  final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> groupingFunction,
                  final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> sortingFunction,
                  final Consumer<RawKey> removeHandler) {
            this.trimmedSize = trimmedSize;
            if (trimmedSize < Integer.MAX_VALUE / 2) {
                this.maxSize = trimmedSize * 2;
            } else {
                this.maxSize = Integer.MAX_VALUE;
            }
            this.itemSerialiser = itemSerialiser;
            this.dataStore = dataStore;
            this.groupingFunction = groupingFunction;
            this.sortingFunction = sortingFunction;
            this.removeHandler = removeHandler;
            list = new ArrayList<>();
        }

        List<byte[]> getList() {
            return list;
        }

//    synchronized void add(final byte[] item) {
//        if (groupingFunction != null || sortingFunction != null) {
//            list.add(item);
//            trimmed = false;
//            if (list.size() > maxSize) {
//                sortAndTrim();
//            }
//        } else if (list.size() < trimmedSize) {
//            list.add(item);
//        } else {
//            full = true;
//            removeHandler.accept(ByteItem.create(item).groupKey);
//        }
//    }

        synchronized void add(final byte[] groupKey, final byte[] generators) {
            if (groupingFunction != null || sortingFunction != null) {
                list.add(itemSerialiser.toBytes(new RawItem(groupKey, generators)));
                trimmed = false;
                if (list.size() > maxSize) {
                    sortAndTrim();
                }
            } else if (list.size() < trimmedSize) {
                list.add(itemSerialiser.toBytes(new RawItem(groupKey, generators)));
            } else {
                full = true;
                removeHandler.accept(new RawKey(groupKey));
            }
        }

        private List<Item> toItemList(final List<byte[]> bytesList) {
            final List<Item> items = new ArrayList<>(bytesList.size());
            for (final byte[] bytes : bytesList) {
                items.add(toItem(bytes));
            }
            return items;
        }
//
//    private List<UnpackedItem> group(final List<byte[]> bytesList) {
//        final Map<RawKey, Generator[]> groupingMap = new HashMap<>();
//        for (final byte[] bytes : bytesList) {
//            final RawItem rawItem = itemSerialiser.readRawItem(bytes);
//            final RawKey rawKey = new RawKey(rawItem.getKey());
//            final Generator[] generators = itemSerialiser.readGenerators(rawItem.getGenerators());
//
//            groupingMap.compute(rawKey, (k, v) -> {
//                Generator[] result = v;
//
//                if (result == null) {
//                    result = generators;
//                } else {
//                    // Combine the new item into the original item.
//                    for (int i = 0; i < result.length; i++) {
//                        Generator existingGenerator = result[i];
//                        Generator newGenerator = generators[i];
//                        if (newGenerator != null) {
//                            if (existingGenerator == null) {
//                                result[i] = newGenerator;
//                            } else {
//                                existingGenerator.merge(newGenerator);
//                            }
//                        }
//                    }
//                }
//
//                return result;
//            });
//        }
//        return groupingMap
//                .entrySet()
//                .parallelStream()
//                .map(e -> new UnpackedItem(e.getKey(), e.getValue(), itemSerialiser.toBytes(e.getValue())))
//                .collect(Collectors.toList());
//    }

        private Item toItem(final byte[] bytes) {
            return new ItemImpl(
                    itemSerialiser,
                    dataStore,
                    bytes);
        }

//    private List<byte[]> toBytesList(final List<Item> itemList) {
//        final List<byte[]> items = new ArrayList<>(itemList.size());
//        for (final Item item : itemList) {
//            items.add(((ItemImpl) item).bytes);
//        }
//        return items;
//    }

        @Override
        public int size() {
            return list.size();
        }

        private synchronized void sortAndTrim() {
            if (!trimmed) {
                // We won't group, sort or trim lists with only a single item obviously.
                if (list.size() > 1) {
                    if (groupingFunction != null || sortingFunction != null) {
                        Stream<UnpackedItem> stream = list
                                .parallelStream()
                                .map(this::unpack);

                        // Group items.
                        if (groupingFunction != null) {
                            stream = groupingFunction.apply(stream);
                        }

                        // Sort the list before trimming if we have a comparator.
                        if (sortingFunction != null) {
                            stream = sortingFunction.apply(stream);
                        }

                        list = stream
                                .map(this::pack)
                                .collect(Collectors.toList());
                    }

                    while (list.size() > trimmedSize) {
                        final byte[] lastItem = list.remove(list.size() - 1);

                        // Tell the remove handler that we have removed an item.
                        removeHandler.accept(toItem(lastItem).getRawKey());
                    }
                }
                trimmed = true;
            }
        }

        private UnpackedItem unpack(final byte[] bytes) {
            final RawItem rawItem = itemSerialiser.readRawItem(bytes);
            final RawKey rawKey = new RawKey(rawItem.getKey());
            final Generator[] generators = itemSerialiser.readGenerators(rawItem.getGenerators());
            return new UnpackedItem(rawKey, generators, bytes);
        }

        private byte[] pack(final UnpackedItem unpackedItems) {
            return unpackedItems.getBytes();
        }

        private synchronized List<Item> copy() {
            sortAndTrim();
            return toItemList(list);
        }

        @Override
        @Nonnull
        public Iterator<Item> iterator() {
//        if (full) {
//            return list.iterator();
//        } else {
            final List<Item> copy = copy();
            return copy.iterator();
//        }
        }
    }

    public static class ItemImpl implements Item {
        private final ItemSerialiser itemSerialiser;
        private final MapDataStore dataStore;
        private final byte[] bytes;

        private RawItem rawItem;
        private RawKey rawKey;
        private Key key;
        private Generator[] generators;
        private Optional<Selection<Val>> childSelection;

        public ItemImpl(final ItemSerialiser itemSerialiser,
                        final MapDataStore dataStore,
                        final byte[] bytes) {
            this.itemSerialiser = itemSerialiser;
            this.dataStore = dataStore;
            this.bytes = bytes;
        }

        RawItem getRawItem() {
            if (rawItem == null) {
                rawItem = itemSerialiser.readRawItem(bytes);
            }
            return rawItem;
        }

        Generator[] getGenerators() {
            if (generators == null) {
                generators = itemSerialiser.readGenerators(getRawItem().getGenerators());
            }
            return generators;
        }

        @Override
        public RawKey getRawKey() {
            if (rawKey == null) {
                rawKey = new RawKey(getRawItem().getKey());
            }
            return rawKey;
        }

        @Override
        public Key getKey() {
            if (key == null) {
                key = itemSerialiser.toKey(getRawKey());
            }
            return key;
        }

        @Override
        public Val getValue(final int index) {
            Val val = null;

            final Generator[] generators = getGenerators();

            if (index >= 0 && index < generators.length) {
                final Generator generator = generators[index];
                if (generator != null) {
                    if (generator instanceof Selector) {
                        if (childSelection == null) {
                            childSelection = Optional.ofNullable(getChildSelection(index));
                        }

                        if (childSelection.isPresent()) {
                            // Make the selector select from the list of child generators.
                            final Selector selector = (Selector) generator;
                            val = selector.select(childSelection.get());

                        } else {
                            // If there are are no child items then just evaluate the inner expression
                            // provided to the selector function.
                            val = generator.eval();
                        }
                    } else {
                        // Convert all list into fully resolved objects evaluating functions where
                        // necessary.
                        val = generator.eval();
                    }
                }
            }
            return val;
        }

        private Selection<Val> getChildSelection(final int index) {
            final Key key = getKey();
            final Key parentKey = key.getParent();

            if (parentKey != null) {
                final RawKey rawParentKey = itemSerialiser.toRawKey(parentKey);

                // If the generator is a selector then select a child row.
                final ItemsImpl childItems = (ItemsImpl) dataStore.get(rawParentKey);
                if (childItems != null) {
                    final List<Item> items = childItems.copy();

                    return new Selection<>() {
                        @Override
                        public int size() {
                            return items.size();
                        }

                        @Override
                        public Val get(final int pos) {
                            return items.get(pos).getValue(index);
                        }
                    };
                }
            }

            return null;
        }
    }

    @Override
    public CompletionState getCompletionState() {
        return completionState;
    }
}
