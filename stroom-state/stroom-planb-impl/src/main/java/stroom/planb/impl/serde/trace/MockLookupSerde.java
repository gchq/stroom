package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.val.VariableValType;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MockLookupSerde implements LookupSerde {

    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final int uidLookupThreshold;
    private final Map<Integer, byte[]> intToBytesMap = new HashMap<>();
    private final Map<byte[], Integer> bytesToIntMap = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final UnsignedBytes lengthWriter;

    public MockLookupSerde() {
        uidLookupThreshold = 32;
        lengthWriter = UnsignedBytesInstances.forValue(uidLookupThreshold);
    }

    @Override
    public int getStorageLength(final byte[] key) {
        if (key.length > USE_HASH_LOOKUP_THRESHOLD) {
            return Integer.BYTES + 1;
        } else if (key.length > uidLookupThreshold) {
            return Integer.BYTES + 1;
        } else {
            return 1 + lengthWriter.length() + key.length;
        }
    }

    @Override
    public byte[] read(final Txn<ByteBuffer> txn,
                       final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        return switch (valType) {
            case DIRECT -> {
                // Read direct.
                final int len = (int) lengthWriter.get(byteBuffer);
                final byte[] bytes = new byte[len];
                byteBuffer.get(bytes);
                yield bytes;
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                yield intToBytesMap.get(byteBuffer.getInt());
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                yield intToBytesMap.get(byteBuffer.getInt());
            }
        };
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final byte[] key, final ByteBuffer byteBuffer) {
        final int len = getStorageLength(key);

        if (len <= uidLookupThreshold) {
            // Add the variable type prefix to the lookup id.
            byteBuffer.put(VariableValType.DIRECT.getPrimitiveValue());
            lengthWriter.put(byteBuffer, key.length);
            byteBuffer.put(key);

        } else {
            if (key.length > USE_HASH_LOOKUP_THRESHOLD) {
                final int i = bytesToIntMap.computeIfAbsent(key, k -> {
                    final int id = counter.incrementAndGet();
                    intToBytesMap.put(id, k);
                    return id;
                });

                // Add the variable type prefix to the lookup id.
                byteBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                byteBuffer.putInt(i);
            } else {
                final int i = bytesToIntMap.computeIfAbsent(key, k -> {
                    final int id = counter.incrementAndGet();
                    intToBytesMap.put(id, k);
                    return id;
                });

                // Add the variable type prefix to the lookup id.
                byteBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                byteBuffer.putInt(i);
            }
        }
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        return switch (valType) {
            case DIRECT -> {
                // Read direct.
                final int len = (int) lengthWriter.get(byteBuffer);
                ByteBufferUtils.skip(byteBuffer, len);
                yield false;
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                ByteBufferUtils.skip(byteBuffer, Integer.BYTES);
                yield true;
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                ByteBufferUtils.skip(byteBuffer, Integer.BYTES);
                yield true;
            }
        };
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return Serde.USED_LOOKUPS_RECORDER;
    }
}
