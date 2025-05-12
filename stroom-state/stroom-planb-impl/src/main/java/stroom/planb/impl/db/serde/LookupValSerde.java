package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.LookupDb;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LookupValSerde implements ValSerde {

    private final LookupDb lookupDb;
    private final ByteBuffers byteBuffers;

    public LookupValSerde(final LookupDb lookupDb, final ByteBuffers byteBuffers) {
        this.lookupDb = lookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = lookupDb.getValue(readTxn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            lookupDb.put(writeTxn, valueByteBuffer, idByteBuffer -> {
                consumer.accept(idByteBuffer);
                return null;
            });
            return null;
        });
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> writeTxn,
                              final Val key,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return ValSerdeUtil.write(key, byteBuffers, valueByteBuffer ->
                lookupDb.get(writeTxn, valueByteBuffer, function));
    }
}
