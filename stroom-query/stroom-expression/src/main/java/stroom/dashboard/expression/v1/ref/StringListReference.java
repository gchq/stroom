package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.List;

public class StringListReference implements ValueReference<List<String>> {

    private final int index;

    StringListReference(final int index) {
        this.index = index;
    }

    @Override
    public List<String> get(final StoredValues storedValues) {
        final Object o = storedValues.get(index);
        if (o == null) {
            return new ArrayList<>();
        }
        return (List<String>) o;
    }

    @Override
    public void set(final StoredValues storedValues, final List<String> value) {
        storedValues.set(index, value);
    }

    @Override
    public void read(final StoredValues storedValues, final Input input) {
        final int length = input.readInt();
        List<String> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(input.readString());
        }
        set(storedValues, list);
    }

    @Override
    public void write(final StoredValues storedValues, final Output output) {
        List<String> list = get(storedValues);
        output.writeInt(list.size());
        for (final String string : list) {
            output.writeString(string);
        }
    }
}
