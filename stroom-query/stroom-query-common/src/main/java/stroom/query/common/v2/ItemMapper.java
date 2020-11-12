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
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Val;
import stroom.mapreduce.v2.MapperBase;
import stroom.mapreduce.v2.OutputCollector;

import java.util.ArrayList;
import java.util.List;

public class ItemMapper extends MapperBase<GroupKey, Val[], GroupKey, Item> {
    private static final Generator[] PARENT_GENERATORS = new Generator[0];

    private final CompiledFields fields;
    private final int maxDepth;
    private final int maxGroupDepth;

    public ItemMapper(final OutputCollector<GroupKey, Item> outputCollector,
                      final CompiledFields fields,
                      final int maxDepth,
                      final int maxGroupDepth) {
        super(outputCollector);
        this.fields = fields;
        this.maxDepth = maxDepth;
        this.maxGroupDepth = maxGroupDepth;
    }

    @Override
    public void map(final GroupKey key, final Val[] values, final OutputCollector<GroupKey, Item> output) {
        // Add the item to the output recursively up to the max depth.
        addItem(values, null, PARENT_GENERATORS, 0, maxDepth, maxGroupDepth, output);
    }

    private void addItem(final Val[] values, final GroupKey parentKey, final Generator[] parentGenerators,
                         final int depth, final int maxDepth, final int maxGroupDepth, final OutputCollector<GroupKey, Item> output) {
        // Process list into fields.
        final Generator[] generators = new Generator[fields.size()];

        List<Val> groupValues = null;
        int pos = 0;
        for (final CompiledField compiledField : fields) {
            Val value = null;

            final Expression expression = compiledField.getExpression();
            if (expression != null) {
                final Generator generator = expression.createGenerator();
                generator.set(values);

                // Only output a value if we are at the group depth or greater
                // for this field, or have a function.
                // If we are applying any grouping then maxDepth will be >= 0.
                if (maxGroupDepth >= depth) {
                    // We always want to output fields that have an aggregate
                    // function or fields that are grouped at the current depth
                    // or above.
                    if (expression.hasAggregate()
                            || (compiledField.getGroupDepth() >= 0 && compiledField.getGroupDepth() <= depth)) {
                        // This field is grouped so output.
                        generators[pos] = generator;
                    }
                } else {
                    // This field is not grouped so output.
                    generators[pos] = generator;
                }

                if (compiledField.getCompiledFilter() != null || compiledField.getGroupDepth() == depth) {
                    // If we are filtering then we need to evaluate this field
                    // now so that we can filter the resultant value.
                    value = generator.eval();

                    if (compiledField.getCompiledFilter() != null && value != null && !compiledField.getCompiledFilter().match(value.toString())) {
                        // We want to exclude this item.
                        return;
                    }
                }
            }

            // If this field is being grouped at this depth then add the value
            // to the group key for this depth.
            if (compiledField.getGroupDepth() == depth) {
                if (groupValues == null) {
                    groupValues = new ArrayList<>();
                }
                groupValues.add(value);
            }

            pos++;
        }

        // Are we grouping this item?
        GroupKey key = null;
        if (parentKey != null || groupValues != null) {
            Val[] arr = null;
            if (groupValues != null) {
                arr = groupValues.toArray(new Val[0]);
            }
            key = new GroupKey(depth, parentKey, arr);
        }

        // If the popToWhenComplete row has child group key sets then add this child group
        // key to them.
        for (final Generator parent : parentGenerators) {
            if (parent != null) {
                parent.addChildKey(key);
            }
        }

        // Add the new item.
        output.collect(key, new Item(key, generators, depth));

        // If we haven't reached the max depth then recurse.
        if (depth < maxDepth) {
            addItem(values, key, generators, depth + 1, maxDepth, maxGroupDepth, output);
        }
    }

    @Override
    public String toString() {
        return "ItemMapper{" +
                "fields=" + fields +
                ", maxDepth=" + maxDepth +
                ", maxGroupDepth=" + maxGroupDepth +
                '}';
    }
}
