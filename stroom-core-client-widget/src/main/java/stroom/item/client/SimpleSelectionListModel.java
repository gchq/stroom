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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleSelectionListModel<T> implements SelectionListModel<T, SimpleSelectionItemWrapper<T>> {

    protected final List<SimpleSelectionItemWrapper<T>> items = new ArrayList<>();

    private SimpleSelectionItemWrapper<T> nonSelectItem;
    private Function<T, String> displayValueFunction = null;
    private Function<T, SafeHtml> renderFunction = null;

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<T> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<T>>> consumer) {
        if (!NullSafe.isBlankString(filter)) {
            final List<SimpleSelectionItemWrapper<T>> filteredItems = items.stream()
                    .filter(item -> item.getLabel().toLowerCase().contains(filter))
                    .collect(Collectors.toList());

            consumer.accept(ResultPage.createUnboundedList(filteredItems));
        } else {
            consumer.accept(ResultPage.createUnboundedList(items));
        }
    }

    @Override
    public void reset() {
    }

    public SimpleSelectionListModel() {
    }

    /**
     * This function will be used to optionally provide a display value for the item. Useful if the item
     * does not implement {@link HasDisplayValue}.
     */
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        this.displayValueFunction = displayValueFunction;
    }

    /**
     * This function will be used to optionally provide a rendered form of the item instead of plain text.
     * If not set then it will fall back on displayValueFunction, {@link HasDisplayValue} or
     * {@link Object#toString()}.
     */
    public void setRenderFunction(final Function<T, SafeHtml> renderFunction) {
        this.renderFunction = renderFunction;
    }

    public void setNonSelectString(final String nonSelectString) {
        nonSelectItem = new SimpleSelectionItemWrapper<>(nonSelectString, null);
        items.add(nonSelectItem);
    }

    public void addItems(final Collection<T> items) {
        for (final T item : items) {
            addItem(item);
        }
    }

    public void addItems(final T[] items) {
        for (final T item : items) {
            addItem(item);
        }
    }

    public void addItem(final T item) {
        if (item != null) {
            items.add(wrap(item));
        }
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
    public SimpleSelectionItemWrapper<T> wrap(final T item) {
        if (item == null) {
            if (nonSelectItem != null) {
                return nonSelectItem;
            }
            return null;
        } else {
            final String displayValue;
            if (displayValueFunction != null) {
                displayValue = displayValueFunction.apply(item);
            } else if (item instanceof HasDisplayValue) {
                displayValue = ((HasDisplayValue) item).getDisplayValue();
            } else if (item instanceof String) {
                displayValue = (String) item;
            } else {
                displayValue = item.toString();
            }
            if (renderFunction != null) {
                return new SimpleSelectionItemWrapper<>(displayValue, item, (label, item1) ->
                        renderFunction.apply(item));
            } else {
                return new SimpleSelectionItemWrapper<>(displayValue, item);
            }
        }
    }

    @Override
    public T unwrap(final SimpleSelectionItemWrapper<T> selectionItem) {
        return NullSafe.get(selectionItem, SimpleSelectionItemWrapper::getItem);
    }

    @Override
    public boolean isEmptyItem(final SimpleSelectionItemWrapper<T> selectionItem) {
        return nonSelectItem != null && nonSelectItem.equals(selectionItem);
    }
}
