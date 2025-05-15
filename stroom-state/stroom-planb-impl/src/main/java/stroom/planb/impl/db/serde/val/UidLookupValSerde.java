package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.UidLookupDb;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UidLookupValSerde implements ValSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;

    public UidLookupValSerde(final UidLookupDb uidLookupDb, final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }

            uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                consumer.accept(idByteBuffer);
                return null;
            });
            return null;
        });
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Val value,
                                final Function<Optional<ByteBuffer>, R> function) {
        return ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > Db.MAX_KEY_LENGTH) {
                throw new RuntimeException("Key length exceeds " + Db.MAX_KEY_LENGTH + " bytes");
            }
            return uidLookupDb.get(txn, valueByteBuffer, function);
        });
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }
}
