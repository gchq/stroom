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
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.query.api.v2.TableSettings;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class TableDataStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableDataStore.class);

    private final Map<RawKey, Items> childMap = new ConcurrentHashMap<>();
    private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();

    private final CompiledField[] compiledFields;
    private final CompiledSorter[] compiledSorters;
    private final CompiledDepths compiledDepths;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final AtomicLong totalResultCount = new AtomicLong();
    private final AtomicLong resultCount = new AtomicLong();
    private final ItemSerialiser itemSerialiser;

    private final GroupingFunction[] groupingFunctions;
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

        groupingFunctions = new GroupingFunction[compiledDepths.getMaxDepth() + 1];
        for (int depth = 0; depth <= compiledDepths.getMaxGroupDepth(); depth++) {
            groupingFunctions[depth] = new GroupingFunction(depth, compiledDepths.getMaxGroupDepth(), compiledFields);
        }

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
        final int count = input.readInt();
        for (int i = 0; i < count; i++) {
            final int length = input.readInt();
            final byte[] bytes = input.readBytes(length);

            final RawItem rawItem = ItemSerialiser.readRawItem(bytes);
            final Key key = rawItem.getGroupKey().toKey();

            KeyPart lastPart = key.getLast();
            if (lastPart != null && !lastPart.isGrouped()) {
                // Ensure sequence numbers are unique for this data store.
                ((UngroupedKeyPart) lastPart).setSequenceNumber(ungroupedItemSequenceNumber.incrementAndGet());
            }

            final Key parent = key.getParent();
            final RawKey parentKey = parent.toRawKey();
            final RawKey groupKey = key.toRawKey();

            final byte[] generators = rawItem.getGenerators();

            addToChildMap(key.getDepth(), parentKey, groupKey, generators);
        }

        // Return success if we have not been asked to terminate and we are still willing to accept data.
        return !Thread.currentThread().isInterrupted() && !hasEnoughData;
    }

    void writePayload(final Output output) {
        final List<byte[]> list = new ArrayList<>();
        childMap.keySet().forEach(groupKey -> {
            final Items items = childMap.remove(groupKey);
            if (items != null) {
                list.addAll(items.getList());
            }
        });

        output.writeInt(list.size());
        for (final byte[] item : list) {
            output.writeInt(item.length);
            output.writeBytes(item);
        }
    }

    void add(final Val[] values) {
        final int[] groupSizeByDepth = compiledDepths.getGroupSizeByDepth();
        final boolean[][] groupIndicesByDepth = compiledDepths.getGroupIndicesByDepth();
        final boolean[][] valueIndicesByDepth = compiledDepths.getValueIndicesByDepth();

        final List<KeyPart> groupKeys = new ArrayList<>(groupIndicesByDepth.length);

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
            if (groupSize > 0) {
                // This is a grouped item.
                keyPart = new GroupKeyPart(groupValues);

            } else {
                // This item will not be grouped.
                keyPart = new UngroupedKeyPart(ungroupedItemSequenceNumber.incrementAndGet());
            }

            final RawKey parentKey = new Key(groupKeys).toRawKey();
            groupKeys.add(keyPart);
            final RawKey childKey = new Key(groupKeys).toRawKey();

            final byte[] generatorBytes = itemSerialiser.toBytes(generators);

            addToChildMap(depth, parentKey, childKey, generatorBytes);
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

    private void addToChildMap(final int depth,
                               final RawKey parentKey,
                               final RawKey groupKey,
                               final byte[] generators) {
        LOGGER.trace(() -> "addToChildMap called for item");
        if (Thread.currentThread().isInterrupted() || hasEnoughData) {
            return;
        }

        // Update the total number of results that we have received.
        totalResultCount.getAndIncrement();

        final GroupingFunction groupingFunction = groupingFunctions[depth];
        final Function<List<Item>, List<Item>> sortingFunction = compiledSorters[depth];

        childMap.compute(parentKey, (k, v) -> {
            Items result = v;

            if (result == null) {
                result = new Items(
                        storeSize.size(depth),
                        itemSerialiser,
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
                final Items items = childMap.remove(parentKey);
                if (items != null) {
                    resultCount.addAndGet(-items.size());
                    items.forEach(item -> remove(item.getGroupKey()));
                }
            });
        }
    }

    public Data getData() {
        return new Data(childMap, resultCount.get(), totalResultCount.get());
    }
}
