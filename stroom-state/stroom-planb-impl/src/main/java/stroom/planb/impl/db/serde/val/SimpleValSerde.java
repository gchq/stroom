package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SimpleValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(size());
    private final ByteBuffers byteBuffers;

    public SimpleValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

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

    @Override
    public final <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                      final Val value,
                                      final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(size(), byteBuffer -> {
            writeVal(byteBuffer, value);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    abstract Val readVal(ByteBuffer byteBuffer);

    abstract void writeVal(ByteBuffer byteBuffer, Val val);

    abstract int size();
}
