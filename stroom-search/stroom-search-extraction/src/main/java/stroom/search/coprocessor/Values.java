package stroom.search.coprocessor;

import stroom.dashboard.expression.v1.Val;

import java.util.Arrays;

public class Values {
    private final Val[] values;

    public Values(final Val[] values) {
        this.values = values;
    }

    public Val[] getValues() {
        return values;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
