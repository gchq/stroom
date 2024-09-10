package stroom.item.client;

import java.util.Collection;
import java.util.function.Function;

public class SelectionBox<T> extends BaseSelectionBox<T, SimpleSelectionItemWrapper<T>> {

    private final SimpleSelectionListModel<T> model = new SimpleSelectionListModel<>();

    public SelectionBox() {
        super();
        setModel(model);
    }

    /**
     * Set this if you want a custom display value for each item.
     * If not set it will do the following:
     * <p>If the item implements {@link stroom.docref.HasDisplayValue} use the displayValue.</p>
     * <p>If the item is a {@link String} use that.</p>
     * <p>Else use the value of {@link Object#toString()}.</p>
     */
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        model.setDisplayValueFunction(displayValueFunction);
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
}
