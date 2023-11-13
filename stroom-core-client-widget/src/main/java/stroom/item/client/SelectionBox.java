package stroom.item.client;

import java.util.Collection;

public class SelectionBox<T> extends AbstractSelectionBox<T> {

    private final SimpleSelectionListModel<T> model = new SimpleSelectionListModel<>();

    public SelectionBox() {
        super();
        setModel(model);
    }

    public void setNonSelectString(final String nonSelectString) {
        model.setNonSelectString(nonSelectString);
    }

    public void addItems(final Collection<T> items) {
        model.addItems(items);
    }

    public void addItems(final T[] items) {
        model.addItems(items);
    }

    public void addItem(final T item) {
        model.addItem(item);
    }

    public void clear() {
        model.clear();
    }

    @Override
    protected SelectionItem wrap(final T item) {
        if (item == null) {
            return null;
        }
        return new SimpleSelectionItemWrapper<>(item);
    }

    @Override
    protected T unwrap(final SelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return ((SimpleSelectionItemWrapper<T>) selectionItem).getItem();
    }
}
