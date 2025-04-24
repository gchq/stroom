package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.TemporalRangedState.Key;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * KEY =   <KEY_START><KEY_END><EFFECTIVE_TIME>
 * VALUE = <VALUE_TYPE><VALUE_BYTES>
 */
public class TemporalRangedStateSerde implements Serde<Key, StateValue> {

    private static final int KEY_LENGTH = Long.BYTES + Long.BYTES + Long.BYTES;

    private final ByteBufferFactory byteBufferFactory;

    public TemporalRangedStateSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    public <T> T createKeyByteBuffer(final Key key, final Function<ByteBuffer, T> function) {
        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
        try {
            keyByteBuffer.putLong(key.getKeyStart());
            keyByteBuffer.putLong(key.getKeyEnd());
            keyByteBuffer.putLong(key.getEffectiveTime());
            keyByteBuffer.flip();
            return function.apply(keyByteBuffer);
        } finally {
            byteBufferFactory.release(keyByteBuffer);
        }
    }

    @Override
    public <R> R createValueByteBuffer(final Key key,
                                       final StateValue value,
                                       final Function<ByteBuffer, R> function) {
        final ByteBuffer valueByteBuffer = byteBufferFactory.acquire(Byte.BYTES +
                                                                     value.getByteBuffer().limit());
        try {
            valueByteBuffer.put(value.getTypeId());
            valueByteBuffer.put(value.getByteBuffer());
            valueByteBuffer.flip();
            return function.apply(valueByteBuffer);
        } finally {
            byteBufferFactory.release(valueByteBuffer);
        }
    }

    @Override
    public <R> R createPrefixPredicate(final Key key, final Function<Predicate<BBKV>, R> function) {
        return function.apply(keyVal -> true);
    }

    @Override
    public void createPrefixPredicate(final BBKV kv, final Consumer<Predicate<BBKV>> consumer) {
        consumer.accept(keyVal -> true);
    }

    @Override
    public boolean hasPrefix() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(final FieldIndex fieldIndex) {
        final Function<KeyVal<ByteBuffer>, Val>[] functions = new Function[fieldIndex.size()];
        for (int i = 0; i < fieldIndex.getFields().length; i++) {
            final String field = fieldIndex.getField(i);
            functions[i] = switch (field) {
                case TemporalRangedStateFields.KEY_START -> kv -> {
                    final long keyStart = kv.key().getLong(0);
                    return ValLong.create(keyStart);
                };
                case TemporalRangedStateFields.KEY_END -> kv -> {
                    final long keyEnd = kv.key().getLong(Long.BYTES);
                    return ValLong.create(keyEnd);
                };
                case TemporalRangedStateFields.EFFECTIVE_TIME -> kv -> {
                    final long effectiveTime = kv.key().getLong(Long.BYTES + Long.BYTES);
                    return ValDate.create(effectiveTime);
                };
                case TemporalRangedStateFields.VALUE_TYPE -> kv -> {
                    final byte typeId = kv.val().get(0);
                    return ValUtil.getType(typeId);
                };
                case TemporalRangedStateFields.VALUE -> kv -> {
                    final byte typeId = kv.val().get(0);
                    final int valueStart = Byte.BYTES;
                    return ValUtil.getValue(typeId, kv.val().slice(valueStart, kv.val().limit() - valueStart));
                };
                default -> byteBuffer -> ValNull.INSTANCE;
            };
        }
        return functions;
    }

    @Override
    public Key getKey(final BBKV kv) {
        final ByteBuffer byteBuffer = kv.key();
        final long keyStart = byteBuffer.getLong(0);
        final long keyEnd = byteBuffer.getLong(Long.BYTES);
        final long effectiveTime = byteBuffer.getLong(Long.BYTES + Long.BYTES);
        return new Key(keyStart, keyEnd, effectiveTime);
    }


    @Override
    public StateValue getVal(final BBKV kv) {
        final ByteBuffer byteBuffer = kv.val();
        final byte typeId = byteBuffer.get(0);
        final int valueStart = Byte.BYTES;
        final ByteBuffer slice = byteBuffer.slice(valueStart, byteBuffer.limit() - valueStart);
        final byte[] valueBytes = ByteBufferUtils.toBytes(slice);
        return new StateValue(typeId, ByteBuffer.wrap(valueBytes));
    }

    @Override
    public int getKeyLength() {
        return KEY_LENGTH;
    }
}
