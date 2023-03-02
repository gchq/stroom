package stroom.dashboard.expression.v1;

import java.util.Arrays;

public class Values {

    private final Val[] values;

    private Values(final Val... values) {
        this.values = values;
    }

    public static Values of(final Val... values) {
        return new Values(values);
    }

    public Val get(int index) {
        return values[index];
    }

    public Val[] toUnsafeArray() {
        return values;
    }

    public int size() {
        return values.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
