package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class FloatValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Float.BYTES);
    private final ByteBuffers byteBuffers;

    public FloatValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Float.BYTES, byteBuffer -> {
//            byteBuffer.putFloat(getFloat(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        ValSerdeUtil.writeFloat(value, reusableWriteBuffer);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Val value,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(Float.BYTES, byteBuffer -> {
            ValSerdeUtil.writeFloat(value, byteBuffer);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
