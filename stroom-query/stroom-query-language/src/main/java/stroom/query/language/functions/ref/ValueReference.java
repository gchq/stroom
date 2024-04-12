package stroom.query.language.functions.ref;

public interface ValueReference<T> {

    T get(StoredValues storedValues);

    void set(StoredValues storedValues, T value);

    void read(StoredValues storedValues, DataReader reader);

    void write(StoredValues storedValues, DataWriter writer);
}
