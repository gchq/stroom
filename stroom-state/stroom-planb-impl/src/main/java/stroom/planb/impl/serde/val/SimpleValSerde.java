package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public abstract class SimpleValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(size());

    @Override
    public final Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        return readVal(byteBuffer);
    }

    @Override
    public final void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writeVal(reusableWriteBuffer, value);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    abstract Val readVal(ByteBuffer byteBuffer);

    abstract void writeVal(ByteBuffer byteBuffer, Val val);

    abstract int size();
}
