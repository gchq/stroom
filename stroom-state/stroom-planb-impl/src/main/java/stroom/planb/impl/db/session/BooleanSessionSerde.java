package stroom.planb.impl.db.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.impl.db.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class BooleanSessionSerde implements SessionSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int length;

    public BooleanSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        length = Byte.BYTES + timeSerde.getSize() + timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    @Override
    public Session read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final Val key = ValBoolean.create(byteBuffer.get() != 0);
        final Instant start = timeSerde.read(byteBuffer);
        final Instant end = timeSerde.read(byteBuffer);
        return new Session(key, start, end);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Session session, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Byte.BYTES, byteBuffer -> {
//            byteBuffer.put(getBoolean(value)
//                    ? (byte) 1
//                    : (byte) 0);
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        ValSerdeUtil.writeBoolean(session.getKey(), reusableWriteBuffer);
        timeSerde.write(reusableWriteBuffer, session.getStart());
        timeSerde.write(reusableWriteBuffer, session.getEnd());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Session session,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            ValSerdeUtil.writeBoolean(session.getKey(), byteBuffer);
            timeSerde.write(byteBuffer, session.getStart());
            timeSerde.write(byteBuffer, session.getEnd());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
