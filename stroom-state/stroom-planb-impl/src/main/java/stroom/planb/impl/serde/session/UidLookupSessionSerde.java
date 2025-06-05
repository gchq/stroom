package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UidLookupSessionSerde implements SessionSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int timeLength;

    public UidLookupSessionSerde(final UidLookupDb uidLookupDb,
                                 final ByteBuffers byteBuffers,
                                 final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        this.timeLength = timeSerde.getSize() + timeSerde.getSize();
    }

    @Override
    public Session read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer startSlice = byteBuffer.slice(byteBuffer.remaining() - timeLength,
                timeSerde.getSize());
        final ByteBuffer endSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Instant start = timeSerde.read(startSlice);
        final Instant end = timeSerde.read(endSlice);

        // Slice off the key.
        final ByteBuffer keySlice = getPrefix(byteBuffer);

        // Read via lookup.
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, keySlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return new Session(KeyPrefix.create(val), start, end);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(0, byteBuffer.remaining() - timeLength);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Session session, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeLength);
            if (slice.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }

            uidLookupDb.put(txn, slice, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeLength, prefixedBuffer -> {
                    prefixedBuffer.put(idByteBuffer);
                    timeSerde.write(prefixedBuffer, session.getStart());
                    timeSerde.write(prefixedBuffer, session.getEnd());
                    prefixedBuffer.flip();
                    consumer.accept(prefixedBuffer);
                });
                return null;
            });
            return null;
        }, prefix, suffix);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Session session,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        return ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            // We are going to store as a lookup so take off the variable type prefix.
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeLength);
            if (slice.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }

            return uidLookupDb.get(txn, slice, optionalIdByteBuffer ->
                    optionalIdByteBuffer
                            .map(idByteBuffer ->
                                    byteBuffers.use(idByteBuffer.remaining() + timeLength, prefixedBuffer -> {
                                        prefixedBuffer.put(idByteBuffer);
                                        timeSerde.write(prefixedBuffer, session.getStart());
                                        timeSerde.write(prefixedBuffer, session.getEnd());
                                        prefixedBuffer.flip();
                                        return function.apply(Optional.of(prefixedBuffer));
                                    }))
                            .orElse(null));
        }, prefix, suffix);
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorderProxy(
                new UidLookupRecorder(env, uidLookupDb),
                this::getPrefix);
    }
}
