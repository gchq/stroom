package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.List;

public class DoubleListReference implements ValueReference<List<Double>> {

    private final int index;

    DoubleListReference(final int index) {
        this.index = index;
    }

    @Override
    public List<Double> get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new ArrayList<>();
        }
        return (List<Double>) o;
    }

    @Override
    public void set(final StoredValues storedValues, final List<Double> value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final Input input) {
        final int length = input.readInt();
        List<Double> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(input.readDouble());
        }
        set(storedValues, list);
    }

    @Override
    public void write(final StoredValues storedValues, final Output output) {
        List<Double> list = get(storedValues);
        output.writeInt(list.size());
        for (final Double d : list) {
            output.writeDouble(d);
        }
    }
}
