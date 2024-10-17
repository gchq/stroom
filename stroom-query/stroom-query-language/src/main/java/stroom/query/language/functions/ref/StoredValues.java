package stroom.query.language.functions.ref;

import java.util.Arrays;

public class StoredValues {

    private final Object[] values;
    private int period;

    public StoredValues(final Object[] values) {
        this.values = values;
    }

    public Object get(final int index) {
        return this.values[index];
    }

    public void set(final int index, final Object val) {
        this.values[index] = val;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(final int period) {
        this.period = period;
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
