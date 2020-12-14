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
import stroom.dashboard.expression.v1.Selection;
import stroom.dashboard.expression.v1.Selector;
import stroom.dashboard.expression.v1.Val;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Items implements Iterable<Item> {
    private final int trimmedSize;
    private final int maxSize;
    private final ItemSerialiser itemSerialiser;
    private final TableDataStore tableDataStore;
    private final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> groupingFunction;
    private final Function<Stream<UnpackedItem>, Stream<UnpackedItem>> sortingFunction;
    private final Consumer<RawKey> removeHandler;

    private volatile List<byte[]> list;

    private volatile boolean trimmed = true;
    private volatile boolean full;

    Items(final int trimmedSize,
          final ItemSerialiser itemSerialiser,
          final TableDataStore tableDataStore,
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
        this.tableDataStore = tableDataStore;
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
                tableDataStore,
                bytes);
    }

//    private List<byte[]> toBytesList(final List<Item> itemList) {
//        final List<byte[]> items = new ArrayList<>(itemList.size());
//        for (final Item item : itemList) {
//            items.add(((ItemImpl) item).bytes);
//        }
//        return items;
//    }

    int size() {
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

    public static class ItemImpl implements Item {
        private final ItemSerialiser itemSerialiser;
        private final TableDataStore tableDataStore;
        private final byte[] bytes;

        private RawItem rawItem;
        private RawKey rawKey;
        private Key key;
        private Generator[] generators;
        private Optional<Selection<Val>> childSelection;

        public ItemImpl(final ItemSerialiser itemSerialiser,
                        final TableDataStore tableDataStore,
                        final byte[] bytes) {
            this.itemSerialiser = itemSerialiser;
            this.tableDataStore = tableDataStore;
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
                final Items childItems = tableDataStore.get(rawParentKey);
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
}
