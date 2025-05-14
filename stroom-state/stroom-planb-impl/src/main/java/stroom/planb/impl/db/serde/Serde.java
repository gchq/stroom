package stroom.planb.impl.db.serde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Serde<T> {

    void write(Txn<ByteBuffer> txn, T value, Consumer<ByteBuffer> consumer);

    T read(Txn<ByteBuffer> txn, ByteBuffer byteBuffer);

    <R> R toBufferForGet(Txn<ByteBuffer> txn, T value, Function<Optional<ByteBuffer>, R> function);

    default boolean usesLookup(ByteBuffer byteBuffer) {
        return false;
    }
}
