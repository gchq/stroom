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
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Selector;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Data {
    public static final GroupKey ROOT_KEY = new GroupKey(-1, null, ValSerialiser.EMPTY_VALUES);

    private final Map<GroupKey, Items> childMap;
    private final long size;
    private final long totalSize;

    public Data(final Map<GroupKey, Items> childMap, final long size, final long totalSize) {
        this.childMap = childMap;
        this.size = size;
        this.totalSize = totalSize;
    }

    public DataItems get() {
        return get(ROOT_KEY);
    }

    public DataItems get(final GroupKey groupKey) {
        final Items items = childMap.get(groupKey);
        return new DataItemsImpl(childMap, items);
    }

    public long getSize() {
        return size;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public interface DataItem {
        GroupKey getKey();

        Val getValue(int index);
    }

    public interface DataItems extends Iterable<DataItem> {
        int size();
    }

    private static class DataItemsImpl implements DataItems {
        private final Map<GroupKey, Items> childMap;
        private final Items items;

        public DataItemsImpl(final Map<GroupKey, Items> childMap,
                             final Items items) {
            this.childMap = childMap;
            this.items = items;
        }

        @Override
        public Iterator<DataItem> iterator() {
            return new Iterator<>() {
                private final Iterator<Item> itemIterator = items == null ? Collections.emptyIterator() : items.iterator();

                @Override
                public boolean hasNext() {
                    return itemIterator.hasNext();
                }

                @Override
                public DataItem next() {
                    final Item item = itemIterator.next();
                    return new DataItemImpl(childMap, item);
                }
            };
        }

        @Override
        public int size() {
            return items == null ? 0 : items.size();
        }
    }

    public static class DataItemImpl implements DataItem {
        private final Map<GroupKey, Items> childMap;
        private final Item item;

        public DataItemImpl(final Map<GroupKey, Items> childMap,
                            final Item item) {
            this.childMap = childMap;
            this.item = item;
        }

        @Override
        public GroupKey getKey() {
            return item.getKey();
        }

        @Override
        public Val getValue(final int index) {
            Val val = null;

            final Generator[] generators = item.getGenerators();
            if (index >= 0 && index < generators.length) {
                final Generator generator = generators[index];
                final GroupKey groupKey = item.getKey();
                if (groupKey != null && generator instanceof Selector) {
                    // If the generator is a selector then select a child row.
                    final Items childItems = childMap.get(groupKey);
                    if (childItems != null) {
                        // Create a list of child generators.
                        final List<Generator> childGenerators = new ArrayList<>(childItems.size());
                        childItems.forEach(childItem -> {
                            final Generator childGenerator = childItem.getGenerators()[index];
                            childGenerators.add(childGenerator);
                        });

                        // Make the selector select from the list of child generators.
                        final Selector selector = (Selector) generator;
                        val = selector.select(childGenerators.toArray(new Generator[0]));

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
            return val;
        }
    }
}
