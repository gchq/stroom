package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.db.VariableUsedLookupsRecorder;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.planb.impl.serde.val.VariableValType;
import stroom.planb.shared.PlanBDoc;
import stroom.query.language.functions.Val;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class VariableKeySerde implements TemporalKeySerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VariableKeySerde.class);

    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final PlanBDoc doc;
    private final int uidLookupThreshold;
    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public VariableKeySerde(final PlanBDoc doc,
                            final UidLookupDb uidLookupDb,
                            final HashLookupDb hashLookupDb,
                            final ByteBuffers byteBuffers,
                            final TimeSerde timeSerde) {
        this.doc = doc;
        this.uidLookupDb = uidLookupDb;
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        uidLookupThreshold = 32 + timeSerde.getSize();
    }

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        try {
            // Make sure the buffer we are reading contains what we expect it to.
            // TODO : We can remove this code when we have diagnosed the issue.
            checkBuffer(byteBuffer.duplicate());

            // Slice off the end to get the effective time.
            final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                    timeSerde.getSize());
            final Instant time = timeSerde.read(timeSlice);

            // Slice off the name.
            final ByteBuffer nameSlice = getPrefixSlice(byteBuffer);

            // Read the variable type.
            final VariableValType valType =
                    VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(nameSlice.get());
            final Val val = switch (valType) {
                case DIRECT -> {
                    // Read direct.
                    yield ValSerdeUtil.read(nameSlice);
                }
                case UID_LOOKUP -> {
                    // Read via UI lookup.
                    final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, nameSlice);
                    yield ValSerdeUtil.read(valueByteBuffer);
                }
                case HASH_LOOKUP -> {
                    // Read via hash lookup.
                    final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, nameSlice);
                    yield ValSerdeUtil.read(valueByteBuffer);
                }
            };

            return new TemporalKey(KeyPrefix.create(val), time);
        } catch (final RuntimeException e) {
            throw new RuntimeException("Unexpected " + e.getMessage() + " reading " +
                                       Arrays.toString(ByteBufferUtils.getBytes(byteBuffer)), e);
        }
    }

    private ByteBuffer getPrefixSlice(final ByteBuffer byteBuffer) {
        // Slice off the name.
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                hashLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(1 + idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, key.getTime());
                        prefixedBuffer.flip();
                        consumer.accept(checkBuffer(prefixedBuffer));
                    });
                    return null;
                });
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                uidLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(1 + idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, key.getTime());
                        prefixedBuffer.flip();
                        consumer.accept(checkBuffer(prefixedBuffer));
                    });
                    return null;
                });
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                consumer.accept(checkBuffer(valueByteBuffer));
            }
            return null;
        }, prefix, suffix);
    }

    private ByteBuffer checkBuffer(final ByteBuffer bb) {
        // TODO : We can remove this code when we have diagnosed the issue.
        if (bb.remaining() > 0) {
            if (bb.get(0) == VariableValType.UID_LOOKUP.getPrimitiveValue()) {
                if (bb.remaining() - 1 - timeSerde.getSize() > 8) {
                    try {
                        throw new IllegalStateException("Unexpected lookup value for '" +
                                                        doc.asDocRef() +
                                                        "' : " +
                                                        Arrays.toString(ByteBufferUtils.toBytes(bb.duplicate())));
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        throw e;
                    }
                }
            }
        }
        return bb;
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final TemporalKey key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        return ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                return hashLookupDb.get(txn, valueSlice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1 + timeSerde.getSize(),
                                                prefixedBuffer -> {
                                                    // Add the variable type prefix to the lookup id.
                                                    prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                                                    prefixedBuffer.put(idByteBuffer);
                                                    timeSerde.write(prefixedBuffer, key.getTime());
                                                    prefixedBuffer.flip();
                                                    return function.apply(Optional.of(prefixedBuffer));
                                                }))
                                .orElse(null));
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                return uidLookupDb.get(txn, valueSlice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1 + timeSerde.getSize(),
                                                prefixedBuffer -> {
                                                    // Add the variable type prefix to the lookup id.
                                                    prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                                                    prefixedBuffer.put(idByteBuffer);
                                                    timeSerde.write(prefixedBuffer, key.getTime());
                                                    prefixedBuffer.flip();
                                                    return function.apply(Optional.of(prefixedBuffer));
                                                }))
                                .orElse(null));
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                return function.apply(Optional.of(valueByteBuffer));
            }
        }, prefix, suffix);
    }

    private ByteBuffer getValueSlice(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(1, byteBuffer.remaining() - 1 - timeSerde.getSize());
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get(0));
        return !VariableValType.DIRECT.equals(valType);
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorderProxy(
                new VariableUsedLookupsRecorder(env, uidLookupDb, hashLookupDb),
                this::getPrefixSlice);
    }
}
