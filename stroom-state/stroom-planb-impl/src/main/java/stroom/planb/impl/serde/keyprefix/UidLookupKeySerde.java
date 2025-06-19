package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UidLookupKeySerde implements KeyPrefixSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;

    public UidLookupKeySerde(final UidLookupDb uidLookupDb, final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Read via lookup.
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, byteBuffer);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return KeyPrefix.create(val);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer -> {
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining());
            KeyLength.check(slice,  Db.MAX_KEY_LENGTH);

            uidLookupDb.put(txn, slice, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining(), prefixedBuffer -> {
                    prefixedBuffer.put(idByteBuffer);
                    prefixedBuffer.flip();
                    consumer.accept(prefixedBuffer);
                });
                return null;
            });
            return null;
        });
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer -> {
            // We are going to store as a lookup so take off the variable type prefix.
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining());
            KeyLength.check(slice,  Db.MAX_KEY_LENGTH);

            return uidLookupDb.get(txn, slice, optionalIdByteBuffer ->
                    optionalIdByteBuffer
                            .map(idByteBuffer ->
                                    byteBuffers.use(idByteBuffer.remaining(), prefixedBuffer -> {
                                        prefixedBuffer.put(idByteBuffer);
                                        prefixedBuffer.flip();
                                        return function.apply(Optional.of(prefixedBuffer));
                                    }))
                            .orElse(null));
        });
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UidLookupRecorder(env, uidLookupDb);
    }
}
