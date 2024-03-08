package stroom.item.client;

import java.util.Objects;

/**
 * An item for use in {@link SelectionBox} that has both a display name and an underlying value.
 */
public class DecoratedItem<T> {
    private final T value;
    private final String displayValue;

    public DecoratedItem(final T value, final String displayValue) {
        this.value = value;
        this.displayValue = displayValue;
    }

    public T getValue() {
        return value;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DecoratedItem<?> that = (DecoratedItem<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return displayValue;
    }
}
