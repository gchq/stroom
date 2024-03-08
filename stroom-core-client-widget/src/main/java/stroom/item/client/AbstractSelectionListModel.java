package stroom.item.client;

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
    public boolean isEmptyItem(final I selectionItem) {
        return false;
    }
}
