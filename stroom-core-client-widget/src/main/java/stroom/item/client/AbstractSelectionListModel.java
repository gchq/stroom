/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.item.client;

import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractSelectionListModel<T, I extends SelectionItem> implements SelectionListModel<T, I> {

    protected final List<I> items = new ArrayList<>();

    @Override
    public void onRangeChange(final I parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<I>> consumer) {
        if (filter != null && !filter.isEmpty()) {
            final List<I> filteredItems = new ArrayList<>(items.size());
            for (final I item : items) {
                if (item.getLabel().toLowerCase().contains(filter)) {
                    filteredItems.add(item);
                }
            }
            consumer.accept(ResultPage.createUnboundedList(filteredItems));
        } else {
            consumer.accept(ResultPage.createUnboundedList(items));
        }
    }

    @Override
    public void reset() {
    }

    public AbstractSelectionListModel() {
    }

    public void addItems(final Collection<T> items) {
        NullSafe.forEach(items, this::addItem);
    }

    public void addItems(final T[] items) {
        NullSafe.forEach(items, this::addItem);
    }

    public void addItem(final T item) {
        NullSafe.consume(item, i -> items.add(wrap(i)));
    }

    public void clear() {
        items.clear();
    }

    @Override
    public boolean displayFilter() {
        return items.size() > 10;
    }

    @Override
    public boolean displayPath() {
        return false;
    }

    @Override
    public boolean displayPager() {
        return items.size() > 100;
    }

    @Override
    public boolean isEmptyItem(final I selectionItem) {
        return false;
    }
}
