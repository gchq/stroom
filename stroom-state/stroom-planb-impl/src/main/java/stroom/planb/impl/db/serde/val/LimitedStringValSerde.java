package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LimitedStringValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final int limit;

    public LimitedStringValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
        this.limit = Db.MAX_KEY_LENGTH;
        reusableWriteBuffer = ByteBuffer.allocateDirect(limit);
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        return ValString.create(ByteBufferUtils.toString(byteBuffer));
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) {
            throw new RuntimeException("Key length exceeds " + limit + " bytes");
        }
//        byteBuffers.use(bytes.length, byteBuffer -> {
//            byteBuffer.put(bytes);
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        reusableWriteBuffer.put(bytes);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Val value,
                                final Function<Optional<ByteBuffer>, R> function) {
        final byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) {
            throw new RuntimeException("Key length exceeds " + limit + " bytes");
        }
        return byteBuffers.use(bytes.length, byteBuffer -> {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
