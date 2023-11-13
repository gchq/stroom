package stroom.item.client;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SimpleSelectionListModel<T> implements SelectionListModel {

    protected final List<SelectionItem> items = new ArrayList<>();

    private String lastFilter;
    private final ListDataProvider<SelectionItem> dataProvider = new ListDataProvider<>();
    private String nonSelectString;

    @Override
    public AbstractDataProvider<SelectionItem> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel getNavigationModel() {
        return null;
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
            final List<SelectionItem> filteredItems = new ArrayList<>(items.size());
            for (final SelectionItem item : items) {
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
        this.nonSelectString = nonSelectString;
    }

    @Override
    public String getNonSelectString() {
        return nonSelectString;
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
            items.add(new SimpleSelectionItemWrapper<>(item));
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
