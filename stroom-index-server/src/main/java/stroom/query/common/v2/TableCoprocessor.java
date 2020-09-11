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

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.mapreduce.v2.BlockingPairQueue;
import stroom.mapreduce.v2.PairQueue;
import stroom.mapreduce.v2.UnsafePairQueue;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;
import stroom.util.shared.HasTerminate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TableCoprocessor implements Coprocessor {
//    private static final GroupKey ROOT_KEY = new GroupKey(null, (List<Val>) null);
//    private static final Generator[] PARENT_GENERATORS = new Generator[0];

    //    private final Map<GroupKey, List<Item>> map = new ConcurrentHashMap<>();
    private final PairQueue<GroupKey, Item> queue;
    private final AtomicInteger count = new AtomicInteger();
    private final ItemMapper mapper;

//    private final LinkedBlockingQueue<Item> q = new LinkedBlockingQueue<>(1000000);

    //    private final HasTerminate hasTerminate;
//    private final CompiledFields compiledFields;
//    private final CompiledDepths compiledDepths;

//    private static final int MAX_SIZE = 1000000;
//    private final Semaphore semaphore = new Semaphore(MAX_SIZE)


//    private final CompiledFields fields;
//    private final int maxDepth;
//    private final int maxGroupDepth;

    public TableCoprocessor(final TableCoprocessorSettings settings,
                            final FieldIndexMap fieldIndexMap,
                            final HasTerminate hasTerminate,
                            final Map<String, String> paramMap) {
        final TableSettings tableSettings = settings.getTableSettings();

        final List<Field> fields = tableSettings.getFields();
//        this.hasTerminate = hasTerminate;
        final CompiledDepths compiledDepths = new CompiledDepths(fields, tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexMap, paramMap);

        queue = new BlockingPairQueue<>(hasTerminate);
        mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth());
//
//        this.fields = compiledFields;
//        this.maxDepth =  compiledDepths.getMaxDepth();
//        this.maxGroupDepth = compiledDepths.getMaxGroupDepth();
    }

    public TableCoprocessor(final PairQueue<GroupKey, Item> queue, final CompiledFields compiledFields, final CompiledDepths compiledDepths) {
        this.queue = queue;
//        this.compiledFields = compiledFields;
//        this.compiledDepths = compiledDepths;
        mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth());
//
//        this.fields = compiledFields;
//        this.maxDepth =  compiledDepths.getMaxDepth();
//        this.maxGroupDepth = compiledDepths.getMaxGroupDepth();
    }

    @Override
    public void receive(final Val[] values) {
        count.incrementAndGet();
        mapper.collect(null, values);

//        final OutputCollector<GroupKey, Item> output = (key, item) -> {
//            // Block if we have too many items in the map.
//            acquire();
//
//            map.compute(key, (k, v) -> {
//                List<Item> result = v;
//
//                // Items with a null key values will not undergo partitioning and reduction as we don't want to
//                // group items with null key values as they are child items.
//                if (k.getValues() != null) {
//                    if (result == null) {
//                        result = Collections.singletonList(item);
//                    } else {
//                        // Combine the new item into the original item.
//                        result = Collections.singletonList(reduce(result.get(0), item));
//                    }
//
//                } else {
//                    if (result == null) {
//                        result = new ArrayList<>();
//                    }
//                    result.add(item);
//                }
//
//                return result;
//            });
//        };
//
//        // Add the item to the output recursively up to the max depth.
//        map(values, null, PARENT_GENERATORS, 0, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth(), queue);
    }


//    private boolean acquire() {
//        try {
//            boolean success = false;
//            while (!success && !hasTerminate.isTerminated()) {
//                success = semaphore.tryAcquire(1, TimeUnit.SECONDS);
//            }
//
//            if (hasTerminate.isTerminated()) {
//                throw new RuntimeException("Search terminated");
//            }
//
//            return success;
//
//        } catch (final InterruptedException e) {
//            throw new RuntimeException("Search terminated");
//        }
//    }
//
//


//
//    public void map(final GroupKey key, final Val[] values, final OutputCollector<GroupKey, Item> output) {
//        // Add the item to the output recursively up to the max depth.
//        addItem(values, null, PARENT_GENERATORS, 0,compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth(), output);
//    }
//
//    private void map(final Val[] values, final GroupKey parentKey, final Generator[] parentGenerators,
//                     final int depth, final int maxDepth, final int maxGroupDepth, final OutputCollector<GroupKey, Item> output) {
//        // Process list into fields.
//        final Generator[] generators = new Generator[compiledFields.size()];
//
//        List<Val> groupValues = null;
//        int pos = 0;
//        for (final CompiledField compiledField : compiledFields) {
//            Val value = null;
//
//            final Expression expression = compiledField.getExpression();
//            if (expression != null) {
//                final Generator generator = expression.createGenerator();
//                generator.set(values);
//
//                // Only output a value if we are at the group depth or greater
//                // for this field, or have a function.
//                // If we are applying any grouping then maxDepth will be >= 0.
//                if (maxGroupDepth >= depth) {
//                    // We always want to output fields that have an aggregate
//                    // function or fields that are grouped at the current depth
//                    // or above.
//                    if (expression.hasAggregate()
//                            || (compiledField.getGroupDepth() >= 0 && compiledField.getGroupDepth() <= depth)) {
//                        // This field is grouped so output.
//                        generators[pos] = generator;
//                    }
//                } else {
//                    // This field is not grouped so output.
//                    generators[pos] = generator;
//                }
//
//                if (compiledField.getCompiledFilter() != null || compiledField.getGroupDepth() == depth) {
//                    // If we are filtering then we need to evaluate this field
//                    // now so that we can filter the resultant value.
//                    value = generator.eval();
//
//                    if (compiledField.getCompiledFilter() != null && value != null && !compiledField.getCompiledFilter().match(value.toString())) {
//                        // We want to exclude this item.
//                        return;
//                    }
//                }
//            }
//
//            // If this field is being grouped at this depth then add the value
//            // to the group key for this depth.
//            if (compiledField.getGroupDepth() == depth) {
//                if (groupValues == null) {
//                    groupValues = new ArrayList<>();
//                }
//                groupValues.add(value);
//            }
//
//            pos++;
//        }
//
//        // Are we grouping this item?
//        GroupKey key = ROOT_KEY;
//        if (parentKey != null || groupValues != null) {
//            key = new GroupKey(parentKey, groupValues);
//        }
//
//        // If the popToWhenComplete row has child group key sets then add this child group
//        // key to them.
//        for (final Generator parent : parentGenerators) {
//            if (parent != null) {
//                parent.addChildKey(key);
//            }
//        }
//
//        // Add the new item.
//        output.collect(key, new Item(key, generators, depth));
//
//        // If we haven't reached the max depth then recurse.
//        if (depth < maxDepth) {
//            map(values, key, generators, depth + 1, maxDepth, maxGroupDepth, output);
//        }
//    }

//    private Item reduce(final Item item1, final Item item2) {
//        // Combine the new item into the original item.
//        for (int i = 0; i < compiledDepths.getDepths().length; i++) {
//            item1.generators[i] = combine(compiledDepths.getDepths()[i], compiledDepths.getMaxDepth(), item1.generators[i], item2.generators[i], item2.depth);
//        }
//        return item1;
//    }
//
//    private Generator combine(final int groupDepth, final int maxDepth, final Generator existingValue,
//                              final Generator addedValue, final int depth) {
//        Generator output = null;
//
//        if (maxDepth >= depth) {
//            if (existingValue != null && addedValue != null) {
//                existingValue.merge(addedValue);
//                output = existingValue;
//            } else if (groupDepth >= 0 && groupDepth <= depth) {
//                // This field is grouped so output existing as it must match the
//                // added value.
//                output = existingValue;
//            }
//        } else {
//            // This field is not grouped so output existing.
//            output = existingValue;
//        }
//
//        return output;
//    }


    @Override
    public Payload createPayload() {
        final UnsafePairQueue<GroupKey, Item> outputQueue = new UnsafePairQueue<>();
        queue.forEach(pair -> outputQueue.collect(pair.getKey(), pair.getValue()));
        // Don't create a payload if the queue is empty.
        if (outputQueue.size() == 0) {
            return null;
        }

        return new TablePayload(outputQueue);


//        // Create a partitioner to perform result reduction if needed.
//        final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
//                compiledDepths.getMaxDepth());
//        partitioner.setOutputCollector(outputQueue);
//
//        // Partition the data prior to forwarding to the target node.
//        partitioner.read(queue);
//
//        // Perform partitioning.
//        partitioner.partition();
//
//        // Don't create a payload if the queue is empty.
//        if (outputQueue.size() == 0) {
//            return null;
//        }
//
//        return new TablePayload(outputQueue);
    }
}
