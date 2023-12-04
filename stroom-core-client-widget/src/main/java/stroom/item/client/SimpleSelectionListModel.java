package stroom.item.client;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class SimpleSelectionListModel<T> implements SelectionListModel<T, SimpleSelectionItemWrapper<T>> {

    protected final List<SimpleSelectionItemWrapper<T>> items = new ArrayList<>();

    private SimpleSelectionItemWrapper<T> nonSelectItem;

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<T> parent,
                              final String filter,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<T>>> consumer) {
        if (filter != null && !filter.isEmpty()) {
            final List<SimpleSelectionItemWrapper<T>> filteredItems = new ArrayList<>(items.size());
            for (final SimpleSelectionItemWrapper<T> item : items) {
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

    public SimpleSelectionListModel() {
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
        }

        if (item instanceof HasDisplayValue) {
            return new SimpleSelectionItemWrapper<>(((HasDisplayValue) item).getDisplayValue(), item);
        }
        return new SimpleSelectionItemWrapper<>(item.toString(), item);
    }

    @Override
    public T unwrap(final SimpleSelectionItemWrapper<T> selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getItem();
    }
}
