package stroom.planb.impl.db.serde.valtime;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.impl.db.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class UidLookupValTimeSerde implements ValTimeSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public UidLookupValTimeSerde(final UidLookupDb uidLookupDb,
                                 final ByteBuffers byteBuffers,
                                 final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public ValTime read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valSlice = byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, valSlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        final Instant insertTime = timeSerde.read(timeSlice);
        return new ValTime(val, insertTime);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final ValTime value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value.val(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }

            uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), keyBuffer -> {
                    keyBuffer.put(idByteBuffer);
                    timeSerde.write(keyBuffer, value.insertTime());
                    keyBuffer.flip();
                    consumer.accept(keyBuffer);
                });

                return null;
            });
            return null;
        });
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }
}
