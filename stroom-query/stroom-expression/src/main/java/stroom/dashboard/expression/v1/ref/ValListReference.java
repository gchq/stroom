package stroom.dashboard.expression.v1.ref;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.List;

public class ValListReference implements ValueReference<List<Val>> {

    private final int index;

    ValListReference(final int index) {
        this.index = index;
    }

    @Override
    public List<Val> get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new ArrayList<>();
        }
        return (List<Val>) o;
    }

    @Override
    public void set(final StoredValues storedValues, final List<Val> value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final Input input) {
        final int length = input.readInt();
        final List<Val> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(ValSerialiser.read(input));
        }
        set(storedValues, list);
    }

    @Override
    public void write(final StoredValues storedValues, final Output output) {
        final List<Val> list = get(storedValues);
        output.writeInt(list.size());
        for (final Val val : list) {
            ValSerialiser.write(output, val);
        }
    }
}
