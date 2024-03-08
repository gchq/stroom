package stroom.item.client;

import stroom.svg.shared.SvgImage;

import java.util.Objects;

public class SimpleSelectionItemWrapper<T> implements SelectionItem {

    private final String label;
    private final T item;

    public SimpleSelectionItemWrapper(final String label, final T item) {
        this.label = label;
        this.item = item;
    }

    public T getItem() {
        return item;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public boolean isHasChildren() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimpleSelectionItemWrapper<?>)) {
            return false;
        }
        final SimpleSelectionItemWrapper<?> that = (SimpleSelectionItemWrapper<?>) o;
        return Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item);
    }

    @Override
    public String toString() {
        return "SimpleSelectionItemWrapper{" +
                "label='" + label + '\'' +
                ", item=" + item +
                '}';
    }
}
