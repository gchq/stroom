package stroom.planb.impl.db.serde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface Serde<T> {

    void write(Txn<ByteBuffer> txn, T value, Consumer<ByteBuffer> consumer);

    T read(Txn<ByteBuffer> txn, ByteBuffer byteBuffer);

    default boolean usesLookup(final ByteBuffer byteBuffer) {
        return false;
    }
}
