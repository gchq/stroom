package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import java.util.Objects;

public class LazyDirectItem implements Item {

    private final Item item;
    private final Val[] values;
    private boolean arrayCreated;

    public LazyDirectItem(final int[] columnIndexMapping,
                          final Item item) {
        this.values = new Val[columnIndexMapping.length];
        this.item = item;
    }

    @Override
    public Key getKey() {
        return item.getKey();
    }

    @Override
    public Val getValue(final int index) {
        Val val = values[index];
        if (val == null) {
            val = Objects.requireNonNullElse(item.getValue(index), ValNull.INSTANCE);
            values[index] = val;
        }
        return val;
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public Val[] toArray() {
        // Force array population.
        if (!arrayCreated) {
            for (int i = 0; i < values.length; i++) {
                getValue(i);
            }
            arrayCreated = true;
        }

        return values;
    }
}
