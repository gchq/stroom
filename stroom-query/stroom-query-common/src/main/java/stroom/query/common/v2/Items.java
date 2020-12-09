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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class Items implements Iterable<Item> {
    private final int trimmedSize;
    private final int maxSize;
    private final ItemSerialiser itemSerialiser;
    private final GroupingFunction groupingFunction;
    private final Function<List<Item>, List<Item>> sortingFunction;
    private final Consumer<RawKey> removeHandler;

    private volatile List<byte[]> list;

    private volatile boolean trimmed = true;
    private volatile boolean full;

    Items(final int trimmedSize,
          final ItemSerialiser itemSerialiser,
          final GroupingFunction groupingFunction,
          final Function<List<Item>, List<Item>> sortingFunction,
          final Consumer<RawKey> removeHandler) {
        this.trimmedSize = trimmedSize;
        if (trimmedSize < Integer.MAX_VALUE / 2) {
            this.maxSize = trimmedSize * 2;
        } else {
            this.maxSize = Integer.MAX_VALUE;
        }
        this.itemSerialiser = itemSerialiser;
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

    synchronized void add(final RawKey groupKey, final byte[] generators) {
        if (groupingFunction != null || sortingFunction != null) {
            list.add(ItemSerialiser.toBytes(new RawItem(groupKey, generators)));
            trimmed = false;
            if (list.size() > maxSize) {
                sortAndTrim();
            }
        } else if (list.size() < trimmedSize) {
            list.add(ItemSerialiser.toBytes(new RawItem(groupKey, generators)));
        } else {
            full = true;
            removeHandler.accept(groupKey);
        }
    }

    private List<Item> toItemList(final List<byte[]> bytesList) {
        final List<Item> items = new ArrayList<>(bytesList.size());
        for (final byte[] bytes : bytesList) {
            items.add(itemSerialiser.readItem(bytes));
        }
        return items;
    }

    private List<byte[]> toBytesList(final List<Item> itemList) {
        final List<byte[]> items = new ArrayList<>(itemList.size());
        for (final Item item : itemList) {
            items.add(itemSerialiser.toBytes(item));
        }
        return items;
    }

    int size() {
        return list.size();
    }

    private synchronized void sortAndTrim() {
        if (!trimmed) {
            // We won't group, sort or trim lists with only a single item obviously.
            if (list.size() > 1) {
                if (groupingFunction != null || sortingFunction != null) {
                    List<Item> items = toItemList(list);

                    // Group items.
                    if (groupingFunction != null) {
                        items = groupingFunction.apply(items);
                    }

                    // Sort the list before trimming if we have a comparator.
                    if (sortingFunction != null) {
                        items = sortingFunction.apply(items);
                    }

                    list = toBytesList(items);
                }

                while (list.size() > trimmedSize) {
                    final byte[] lastItem = list.remove(list.size() - 1);

                    // Tell the remove handler that we have removed an item.
                    removeHandler.accept(ItemSerialiser.readRawItem(lastItem).getGroupKey());
                }
            }
            trimmed = true;
        }
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
