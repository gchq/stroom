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
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.query.api.v2.TableSettings;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class TableDataStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableDataStore.class);

    private final Map<GroupKey, Items> childMap = new ConcurrentHashMap<>();

    private final CompiledField[] compiledFields;
    private final CompiledSorter[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final ItemSerialiser itemSerialiser;

    private final Function<List<Item>, List<Item>> groupingFunction;
    private final boolean hasSort;

    private volatile boolean hasEnoughData;

    public TableDataStore(final TableSettings tableSettings,
                          final FieldIndex fieldIndex,
                          final Map<String, String> paramMap,
                          final Sizes maxResults,
                          final Sizes storeSize) {
        compiledFields = CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);
        final CompiledDepths compiledDepths = new CompiledDepths(compiledFields, tableSettings.showDetail());
        this.compiledSorters = CompiledSorter.create(compiledDepths.getMaxDepth(), compiledFields);
        this.compiledDepths = compiledDepths;
        this.maxResults = maxResults;
        this.storeSize = storeSize;
        itemSerialiser = new ItemSerialiser(compiledFields);

        groupingFunction = createGroupingFunction();

        // Find out if we have any sorting.
        boolean hasSort = false;
        for (final CompiledSorter sorter : compiledSorters) {
            if (sorter != null) {
                hasSort = true;
                break;
            }
        }
        this.hasSort = hasSort;
    }

    void clear() {
        totalResultCount.set(0);
        childMap.clear();
    }

    boolean readPayload(final Input input) {
        final Item[] itemsArray = itemSerialiser.readArray(input);
        for (final Item item : itemsArray) {
            addToChildMap(item);
        }

        // Return success if we have not been asked to terminate and we are still willing to accept data.
        return !Thread.currentThread().isInterrupted() && !hasEnoughData;
    }

    void writePayload(final Output output) {
        final List<Item> itemList = new ArrayList<>();
        childMap.keySet().forEach(groupKey -> {
            final Items items = childMap.remove(groupKey);
            if (items != null) {
                items.forEach(itemList::add);
            }
        });

        Item[] itemsArray = new Item[0];
        if (itemList.size() > 0) {
            itemsArray = itemList.toArray(new Item[0]);
        }

        itemSerialiser.writeArray(output, itemsArray);
    }

    void add(final Val[] values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        GroupKey parentKey = null;

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

            final GroupKey key = new GroupKey(depth, parentKey, groupValues);
            parentKey = key;

            final Item item = new Item(key, generators);
            addToChildMap(item);
        }
    }

    public void write(final Val[] values,
                      final Output output) {
        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();
            if (expression != null) {
                final Generator generator = expression.createGenerator();
                generator.set(values);
                generator.write(output);
            }
        }
    }

    public Generator[] read(final Input input) {
        // Process list into fields.
        final Generator[] generators = new Generator[compiledFields.length];
        for (int i = 0; i < compiledFields.length; i++) {
            final CompiledField compiledField = compiledFields[i];
            final Expression expression = compiledField.getExpression();
            if (expression != null) {
                final Generator generator = expression.createGenerator();
                generator.read(input);
                generators[i] = generator;
            }
        }
        return generators;
    }

    private void addToChildMap(final Item item) {
        LOGGER.trace(() -> "addToChildMap called for item");
        if (Thread.currentThread().isInterrupted() || hasEnoughData) {
            return;
        }

        // Update the total number of results that we have received.
        totalResultCount.getAndIncrement();

        GroupKey parentKey;
        if (item.getKey().getParent() != null) {
            parentKey = item.getKey().getParent();
        } else {
            parentKey = Data.ROOT_KEY;
        }

        final Function<List<Item>, List<Item>> groupingFunction = getGroupingFunction(item.getKey());
        final Function<List<Item>, List<Item>> sortingFunction = compiledSorters[item.getKey().getDepth()];

        childMap.compute(parentKey, (k, v) -> {
            Items result = v;

            if (result == null) {
                result = new Items(
                        storeSize.size(item.getKey().getDepth()),
                        itemSerialiser,
                        groupingFunction,
                        sortingFunction,
                        removed ->
                                remove(removed.getKey()));
                result.add(item);
                resultCount.incrementAndGet();

            } else {
                result.add(item);
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

    private Function<List<Item>, List<Item>> getGroupingFunction(final GroupKey groupKey) {
        if (groupKey.getValues() != null && groupKey.getValues().length > 0) {
            return groupingFunction;
        }
        return null;
    }

    private Function<List<Item>, List<Item>> createGroupingFunction() {
        return this::groupItems;
    }

    private List<Item> groupItems(final List<Item> list) {
        // Group items in the list.
        final Map<GroupKey, Item> groupingMap = new HashMap<>();
        for (final Item item : list) {
            final GroupKey key = item.getKey();
            groupingMap.compute(key, (k, v) -> {
                Item result = v;

                if (result == null) {
                    result = item;
                } else {
                    // Combine the new item into the original item.
                    final Generator[] existingGenerators = result.getGenerators();
                    final Generator[] newGenerators = item.getGenerators();
                    final Generator[] combinedGenerators = new Generator[existingGenerators.length];
                    for (int i = 0; i < compiledFields.length; i++) {
                        final CompiledField compiledField = compiledFields[i];
                        combinedGenerators[i] = combine(
                                compiledField.getGroupDepth(),
                                compiledDepths.getMaxDepth(),
                                existingGenerators[i],
                                newGenerators[i],
                                key.getDepth());
                    }
                    result = new Item(k, combinedGenerators);
                }

                return result;
            });
        }
        return new ArrayList<>(groupingMap.values());
    }

    private Generator combine(final int groupDepth,
                              final int maxDepth,
                              final Generator existingValue,
                              final Generator addedValue,
                              final int depth) {
        Generator output = null;

        if (maxDepth >= depth) {
            if (existingValue != null && addedValue != null) {
                existingValue.merge(addedValue);
                output = existingValue;
            } else if (groupDepth >= 0 && groupDepth <= depth) {
                // This field is grouped so output existing as it must match the
                // added value.
                output = existingValue;
            }
        } else {
            // This field is not grouped so output existing.
            output = existingValue;
        }

        return output;
    }

    private void remove(final GroupKey groupKey) {
        if (groupKey != null) {
            // Execute removal asynchronously to prevent blocking.
            CompletableFuture.runAsync(() -> {
                final Items items = childMap.remove(groupKey);
                if (items != null) {
                    resultCount.addAndGet(-items.size());
                    items.forEach(item -> remove(item.getKey()));
                }
            });
        }
    }

    public Data getData() {
        return new Data(childMap, resultCount.get(), totalResultCount.get());
    }
}
