package stroom.planb.impl.serde.val;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.planb.impl.db.Db;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class LimitedStringValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final int limit;

    public LimitedStringValSerde() {
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

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        reusableWriteBuffer.put(bytes);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }
}
