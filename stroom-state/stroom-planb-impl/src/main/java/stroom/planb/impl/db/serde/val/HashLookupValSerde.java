package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashLookupDb;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class HashLookupValSerde implements ValSerde {

    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;

    public HashLookupValSerde(final HashLookupDb hashLookupDb, final ByteBuffers byteBuffers) {
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            hashLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
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
        return ValSerdeUtil.write(value, byteBuffers, valueByteBuffer ->
                hashLookupDb.get(txn, valueByteBuffer, function));
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }
}
