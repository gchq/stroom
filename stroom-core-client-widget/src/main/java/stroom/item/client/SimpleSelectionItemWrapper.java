package stroom.item.client;

import stroom.docref.HasDisplayValue;
import stroom.svg.shared.SvgImage;

public class SimpleSelectionItemWrapper<T> implements SelectionItem {

    private final T item;

    public SimpleSelectionItemWrapper(final T item) {
        this.item = item;
    }

    public T getItem() {
        return item;
    }

    @Override
    public String getLabel() {
        if (item instanceof HasDisplayValue) {
            return ((HasDisplayValue) item).getDisplayValue();
        } else {
            return item.toString();
        }
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }
}