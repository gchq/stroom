package stroom.query.language.functions.ref;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public interface ValueReference<T> {
    T get(StoredValues storedValues);

    void set(StoredValues storedValues, T value);

    void read(StoredValues storedValues, Input input);

    void write(StoredValues storedValues, Output output);
}
