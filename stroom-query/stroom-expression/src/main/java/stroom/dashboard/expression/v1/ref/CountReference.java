package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class CountReference implements ValueReference<Long> {
    private final int index;

    CountReference(final int index) {
        this.index = index;
    }

    public void increment(final StoredValues storedValues) {
        add(storedValues, 1);
    }

    public void add(final StoredValues storedValues, final long diff) {
        set(storedValues, get(storedValues) + diff);
    }

    @Override
    public Long get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return 0L;
        }
        return (long) o;
    }

    @Override
    public void set(final StoredValues storedValues, final Long value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final Input input) {
        set(storedValues, input.readLong());
    }

    @Override
    public void write(final StoredValues storedValues, final Output output) {
        output.writeLong(get(storedValues));
    }
}
