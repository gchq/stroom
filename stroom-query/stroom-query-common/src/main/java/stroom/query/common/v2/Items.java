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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

class Items implements Iterable<Item> {
    private final List<Item> list;
    private final int trimmedSize;
    private final int maxSize;
    private final Comparator<Item> comparator;
    private final Consumer<Item> removeHandler;

    private volatile boolean trimmed = true;
    private volatile boolean full;

    Items(final int trimmedSize,
          final Comparator<Item> comparator,
          final Consumer<Item> removeHandler) {
        this.trimmedSize = trimmedSize;
        this.maxSize = trimmedSize * 2;
        this.comparator = comparator;
        this.removeHandler = removeHandler;
        list = new ArrayList<>();
    }

    synchronized void add(final Item item) {
        if (comparator != null) {
            list.add(item);
            trimmed = false;
            if (list.size() > maxSize) {
                sortAndTrim();
            }
        } else if (list.size() < trimmedSize) {
            list.add(item);
        } else {
            full = true;
            removeHandler.accept(item);
        }
    }

    int size() {
        return list.size();
    }

    private void sortAndTrim() {
        if (!trimmed) {
            // Sort the list before trimming if we have a comparator.
            list.sort(comparator);
            while (list.size() > trimmedSize) {
                final Item lastItem = list.remove(list.size() - 1);

                // Tell the remove handler that we have removed an item.
                removeHandler.accept(lastItem);
            }
            trimmed = true;
        }
    }

    private synchronized List<Item> copy() {
        sortAndTrim();
        return new ArrayList<>(list);
    }

    @Override
    @Nonnull
    public Iterator<Item> iterator() {
        if (full) {
            return list.iterator();
        } else {
            final List<Item> copy = copy();
            return copy.iterator();
        }
    }
}
