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

package stroom.query;

import stroom.dashboard.expression.Expression;
import stroom.dashboard.expression.Generator;
import stroom.mapreduce.MapperBase;
import stroom.mapreduce.OutputCollector;

import java.util.ArrayList;
import java.util.List;

public class ItemMapper extends MapperBase<Object, String[], Key, Item> {
    private final CompiledFields fields;
    private final int maxDepth;
    private final int maxGroupDepth;

    public ItemMapper(final OutputCollector<Key, Item> outputCollector, final CompiledFields fields,
                      final int maxDepth, final int maxGroupDepth) {
        super(outputCollector);
        this.fields = fields;
        this.maxDepth = maxDepth;
        this.maxGroupDepth = maxGroupDepth;
    }

    @Override
    public void map(final Object key, final String[] values, final OutputCollector<Key, Item> output) {
        // Add the item to the output recursively up to the max depth.
        addItem(values, null, null, 0, maxDepth, maxGroupDepth, output);
    }

    private void addItem(final String[] values, final Key parentKey, final Generator[] parentGenerators,
                         final int depth, final int maxDepth, final int maxGroupDepth, final OutputCollector<Key, Item> output) {
        // Process list into fields.
        final Generator[] generators = new Generator[fields.size()];

        List<Object> groupValues = null;
        int pos = 0;
        for (final CompiledField compiledField : fields) {
            Object value = null;

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
        Key key = null;
        if (parentKey != null || groupValues != null) {
            key = new Key(parentKey, groupValues);
        }

        // If the parent row has child group key sets then add this child group
        // key to them.
        if (parentGenerators != null) {
            for (final Generator parent : parentGenerators) {
                if (parent != null && parent instanceof Generator) {
                    parent.addChildKey(key);
                }
            }
        }

        // Add the new item.
        output.collect(key, new Item(key, generators, depth));

        // If we haven't reached the max depth then recurse.
        if (depth < maxDepth) {
            addItem(values, key, generators, depth + 1, maxDepth, maxGroupDepth, output);
        }
    }
}
