package stroom.planb.impl.db.serde;

import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ValSerde {

    Val toVal(Txn<ByteBuffer> txn, ByteBuffer byteBuffer);

    void toBuffer(Txn<ByteBuffer> txn, Val value, Consumer<ByteBuffer> consumer);

    Val toBufferForGet(Txn<ByteBuffer> txn, Val value, Function<Optional<ByteBuffer>, Val> function);
}
