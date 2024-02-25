package stroom.item.client;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

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

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<T> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<T>>> consumer) {
        if (!GwtNullSafe.isBlankString(filter)) {
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
     * This function will be used to provide a display value for the item. Useful if the item
     * does not implement {@link HasDisplayValue}.
     */
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        this.displayValueFunction = displayValueFunction;
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
            return new SimpleSelectionItemWrapper<>(displayValue, item);
        }
    }

    @Override
    public T unwrap(final SimpleSelectionItemWrapper<T> selectionItem) {
        return GwtNullSafe.get(selectionItem, SimpleSelectionItemWrapper::getItem);
    }

    @Override
    public boolean isEmptyItem(final SimpleSelectionItemWrapper<T> selectionItem) {
        return nonSelectItem != null && nonSelectItem.equals(selectionItem);
    }
}
