package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LimitedStringKeySerde implements KeyPrefixSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final int limit;

    public LimitedStringKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
        this.limit = Db.MAX_KEY_LENGTH;
        reusableWriteBuffer = ByteBuffer.allocateDirect(limit);
    }

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Read via lookup.
        final Val val = ValString.create(ByteBufferUtils.toString(byteBuffer));
        return KeyPrefix.create(val);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = key.getVal().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length,  limit);

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        reusableWriteBuffer.put(bytes);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final byte[] bytes = key.getVal().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length,  limit);

        return byteBuffers.use(bytes.length, byteBuffer -> {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
