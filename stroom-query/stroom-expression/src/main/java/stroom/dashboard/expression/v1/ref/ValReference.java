package stroom.dashboard.expression.v1.ref;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class ValReference implements ValueReference<Val> {

    private final int index;

    ValReference(final int index) {
        this.index = index;
    }

    @Override
    public Val get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return ValNull.INSTANCE;
        }
        return (Val) o;
    }

    @Override
    public void set(final StoredValues storedValues, final Val value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final Input input) {
        set(storedValues, ValSerialiser.read(input));
    }

    @Override
    public void write(final StoredValues storedValues, final Output output) {
        ValSerialiser.write(output, get(storedValues));
    }
}
