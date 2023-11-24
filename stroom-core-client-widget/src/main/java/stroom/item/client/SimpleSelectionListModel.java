package stroom.item.client;

import stroom.docref.HasDisplayValue;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SimpleSelectionListModel<T> implements SelectionListModel<T, SimpleSelectionItemWrapper<T>> {

    protected final List<SimpleSelectionItemWrapper<T>> items = new ArrayList<>();

    private String lastFilter;
    private final ListDataProvider<SimpleSelectionItemWrapper<T>> dataProvider = new ListDataProvider<>();

    @Override
    public AbstractDataProvider<SimpleSelectionItemWrapper<T>> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel<SimpleSelectionItemWrapper<T>> getNavigationModel() {
        return null;
    }

    private SimpleSelectionItemWrapper<T> nonSelectItem;

    @Override
    public void reset() {
        lastFilter = null;
    }

    @Override
    public void setFilter(final String filter) {
        if (!Objects.equals(filter, lastFilter)) {
            lastFilter = filter;
            refresh();
        }
    }

    @Override
    public void refresh() {
        if (lastFilter != null && !lastFilter.isEmpty()) {
            final List<SimpleSelectionItemWrapper<T>> filteredItems = new ArrayList<>(items.size());
            for (final SimpleSelectionItemWrapper<T> item : items) {
                if (item.getLabel().toLowerCase().contains(lastFilter)) {
                    filteredItems.add(item);
                }
            }
            dataProvider.setList(filteredItems);
        } else {
            dataProvider.setList(items);
        }
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
