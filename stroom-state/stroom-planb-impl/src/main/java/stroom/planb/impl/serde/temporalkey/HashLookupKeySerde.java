package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.HashLookupRecorder;
import stroom.planb.impl.db.PlanBEnv;
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

public class HashLookupKeySerde implements TemporalKeySerde {

    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public HashLookupKeySerde(final HashLookupDb hashLookupDb,
                              final ByteBuffers byteBuffers,
                              final TimeSerde timeSerde) {
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Slice off the end to get the effective time.
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Instant time = timeSerde.read(timeSlice);

        // Slice off the name.
        final ByteBuffer nameSlice = getPrefix(byteBuffer);

        // Read via lookup.
        final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, nameSlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return new TemporalKey(KeyPrefix.create(val), time);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeSerde.getSize());
            hashLookupDb.put(txn, slice, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                    prefixedBuffer.put(idByteBuffer);
                    timeSerde.write(prefixedBuffer, key.getTime());
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
                                final TemporalKey key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        return ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            // We are going to store as a lookup so take off the variable type prefix.
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeSerde.getSize());
            return hashLookupDb.get(txn, slice, optionalIdByteBuffer ->
                    optionalIdByteBuffer
                            .map(idByteBuffer ->
                                    byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                                        prefixedBuffer.put(idByteBuffer);
                                        timeSerde.write(prefixedBuffer, key.getTime());
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
                new HashLookupRecorder(env, hashLookupDb),
                this::getPrefix);
    }
}
