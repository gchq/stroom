package stroom.item.client;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class AbstractSelectionListModel<T, I extends SelectionItem> implements SelectionListModel<T, I> {

    protected final List<I> items = new ArrayList<>();

    private String lastFilter;
    private final ListDataProvider<I> dataProvider = new ListDataProvider<>();

    @Override
    public AbstractDataProvider<I> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel<I> getNavigationModel() {
        return null;
    }

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
            final List<I> filteredItems = new ArrayList<>(items.size());
            for (final I item : items) {
                if (item.getLabel().toLowerCase().contains(lastFilter)) {
                    filteredItems.add(item);
                }
            }
            dataProvider.setList(filteredItems);
        } else {
            dataProvider.setList(items);
        }
    }

    public AbstractSelectionListModel() {
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
}
