package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SimpleKeyPrefixSerde implements KeyPrefixSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final int length;

    public SimpleKeyPrefixSerde(final ByteBuffers byteBuffers,
                                final int prefixLength) {
        this.byteBuffers = byteBuffers;
        length = prefixLength;
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    abstract Val readPrefix(final ByteBuffer byteBuffer);

    abstract void writePrefix(final Val val, final ByteBuffer byteBuffer);

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final Val prefix = readPrefix(byteBuffer);
        return KeyPrefix.create(prefix);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writePrefix(key.getVal(), reusableWriteBuffer);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writePrefix(key.getVal(), byteBuffer);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
