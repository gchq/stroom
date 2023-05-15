package stroom.dashboard.expression.v1.ref;

import java.util.Arrays;

public class StoredValues {

    private final Object[] values;

    public StoredValues(final Object[] values) {
        this.values = values;
    }

    public Object get(final int index) {
        return this.values[index];
    }

    public void set(final int index, final Object val) {
        this.values[index] = val;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StoredValues that = (StoredValues) o;
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
