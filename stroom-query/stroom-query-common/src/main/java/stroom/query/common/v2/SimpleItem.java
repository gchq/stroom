package stroom.query.common.v2;

import stroom.query.language.functions.Val;

public class SimpleItem implements Item {

    private final Key key;
    private final Val[] values;

    public SimpleItem(final Key key, final Val[] values) {
        this.key = key;
        this.values = values;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public Val getValue(final int index) {
        return values[index];
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public Val[] toArray() {
        return values;
    }
}
