package stroom.query.language.functions.ref;

import java.util.ArrayList;
import java.util.List;

public class StringListReference implements ValueReference<List<String>> {

    private final int index;
    private final String name;

    StringListReference(final int index, final String name) {
        this.index = index;
        this.name = name;
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
    public void read(final StoredValues storedValues, final DataReader reader) {
        final int length = reader.readInt();
        final List<String> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(reader.readString());
        }
        set(storedValues, list);
    }

    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        final List<String> list = get(storedValues);
        writer.writeInt(list.size());
        for (final String string : list) {
            writer.writeString(string);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
