package stroom.query.language.functions.ref;

public class CountReference implements ValueReference<Long> {

    private final int index;
    private final String name;

    CountReference(final int index, final String name) {
        this.index = index;
        this.name = name;
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
    public void read(final StoredValues storedValues, final DataReader reader) {
        set(storedValues, reader.readLong());
    }

    @Override
    public void write(final StoredValues storedValues, final DataWriter writer) {
        writer.writeLong(get(storedValues));
    }

    @Override
    public String toString() {
        return name;
    }
}
