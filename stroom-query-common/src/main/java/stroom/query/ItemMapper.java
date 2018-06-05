/*
 * Copyright 2016 Crown Copyright
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

package stroom.query;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Key;
import stroom.dashboard.expression.v1.Val;
import stroom.mapreduce.MapperBase;
import stroom.mapreduce.OutputCollector;

import java.util.Objects;

public class ItemMapper extends MapperBase<Object, Val[], String, Item> {
    private static final Generator[] PARENT_GENERATORS = new Generator[0];

    private final CompiledFields fields;
    private final int maxDepth;
    private final int maxGroupDepth;

    public ItemMapper(final OutputCollector<String, Item> outputCollector,
                      final CompiledFields fields,
                      final int maxDepth,
                      final int maxGroupDepth) {
        super(outputCollector);
        this.fields = fields;
        this.maxDepth = maxDepth;
        this.maxGroupDepth = maxGroupDepth;
    }

    @Override
    public void map(final Object key, final Val[] values, final OutputCollector<String, Item> output) {
        // Add the item to the output recursively up to the max depth.
        addItem(values, null, PARENT_GENERATORS, 0, maxDepth, maxGroupDepth, output);
    }

    private void addItem(final Val[] values, final String parentKey, final Generator[] parentGenerators,
                         final int depth, final int maxDepth, final int maxGroupDepth, final OutputCollector<String, Item> output) {
        // Process values into fields.
        final Generator[] generators = new Generator[fields.size()];

        StringBuilder sb = null;
        int pos = 0;
        for (final CompiledField compiledField : fields) {
            String stringValue = null;

            final Expression expression = compiledField.getExpression();
            if (expression != null) {
                final Generator generator = expression.createGenerator();
                generator.set(values);

                // Only output a value if we are at the group depth or greater for this field, or have a function.
                // If we are applying any grouping then maxDepth will be >= 0.
                if (maxGroupDepth >= depth) {
                    // We always want to output fields that have an aggregate function or fields that are grouped at the
                    // current depth or above.
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
                    // If we are filtering then we need to evaluate this field now so that we can filter the resultant
                    // value.
                    final Object o = generator.eval();
                    if (o != null) {
                        stringValue = o.toString();
                    } else {
                        stringValue = null;
                    }

                    if (compiledField.getCompiledFilter() != null
                            && !compiledField.getCompiledFilter().match(stringValue)) {
                        // We want to exclude this item.
                        return;
                    }
                }
            }

            // If this field is being grouped at this depth then add the value to the group key for this depth.
            if (compiledField.getGroupDepth() == depth) {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                if (stringValue != null) {
                    sb.append(stringValue);
                }
                sb.append("|");
            }

            pos++;
        }

        // Are we grouping this item?
        String groupKey = null;
        if (sb != null) {
            // take off last pipe.
            sb.setLength(sb.length() - 1);
            if (parentKey != null) {
                sb.insert(0, ":");
                sb.insert(0, parentKey);
            }

            groupKey = sb.toString();
        }

        // If the parent row has child group key sets then add this child group key to them.
        final Key childKey = GroupKey.create(groupKey);
        for (final Generator parent : parentGenerators) {
            if (parent != null) {
                parent.addChildKey(childKey);
            }
        }

        // Add the new item.
        output.collect(groupKey, new Item(parentKey, groupKey, generators, depth));

        // If we haven't reached the max depth then recurse.
        if (depth < maxDepth) {
            addItem(values, groupKey, generators, depth + 1, maxDepth, maxGroupDepth, output);
        }
    }

    private static final class GroupKey implements Key {
        private final String string;

        private GroupKey(final String string) {
            this.string = string;
        }

        static Key create(final String string) {
            if (string == null) {
                return null;
            }
            return new GroupKey(string);
        }

        @Override
        public int hashCode() {
            return string.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final GroupKey key = (GroupKey) o;
            return Objects.equals(string, key.string);
        }

        @Override
        public String toString() {
            return string;
        }
    }
}
