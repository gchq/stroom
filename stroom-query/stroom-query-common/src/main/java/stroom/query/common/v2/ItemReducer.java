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

import stroom.dashboard.expression.v1.Generator;
import stroom.mapreduce.v2.OutputCollector;
import stroom.mapreduce.v2.Reducer;

public class ItemReducer implements Reducer<GroupKey, Item, GroupKey, Item> {
    private final int[] depths;
    private final int maxDepth;

    public ItemReducer(final int[] depths, final int maxDepth) {
        this.depths = depths;
        this.maxDepth = maxDepth;
    }

    @Override
    public void reduce(final GroupKey key, final Iterable<Item> values, final OutputCollector<GroupKey, Item> output) {
        Item dest = null;
        for (final Item item : values) {
            if (dest == null) {
                dest = item;

            } else {
                // Combine new list into original item list.
                for (int i = 0; i < depths.length; i++) {
                    dest.generators[i] = combine(depths[i], maxDepth, dest.generators[i], item.generators[i], item.depth);
                }
            }
        }

        output.collect(key, dest);
    }

    private Generator combine(final int groupDepth, final int maxDepth, final Generator existingValue,
                              final Generator addedValue, final int depth) {
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
}
