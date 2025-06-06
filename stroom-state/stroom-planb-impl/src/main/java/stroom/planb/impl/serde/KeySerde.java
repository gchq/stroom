package stroom.planb.impl.serde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

public interface KeySerde<T> extends Serde<T> {

    <R> R toBufferForGet(Txn<ByteBuffer> txn, T value, Function<Optional<ByteBuffer>, R> function);
}
