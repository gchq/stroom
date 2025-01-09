package stroom.planb.impl.io;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.RangedState.Key;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * KEY =   <KEY_START><KEY_END>
 * VALUE = <VALUE_TYPE><VALUE_BYTES>
 */
public class RangedStateSerde implements Serde<Key, StateValue> {

    private final ByteBufferFactory byteBufferFactory;

    public RangedStateSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    public <T> T createKeyByteBuffer(final Key key, final Function<ByteBuffer, T> function) {
        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);
        try {
            keyByteBuffer.putLong(key.keyStart());
            keyByteBuffer.putLong(key.keyEnd());
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
                                                                     value.byteBuffer().limit());
        try {
            valueByteBuffer.put(value.typeId());
            valueByteBuffer.put(value.byteBuffer());
            valueByteBuffer.flip();
            return function.apply(valueByteBuffer);
        } finally {
            byteBufferFactory.release(valueByteBuffer);
        }
    }

    @Override
    public <R> R createPrefixPredicate(final Key key,
                                       final Function<Predicate<KeyVal<ByteBuffer>>, R> function) {
        return function.apply(keyVal -> true);
    }

    @Override
    public <R> R createPrefixPredicate(final ByteBuffer keyByteBuffer,
                                       final ByteBuffer valueByteBuffer,
                                       final Function<Predicate<KeyVal<ByteBuffer>>, R> function) {
        return function.apply(keyVal -> true);
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
                case RangedStateFields.KEY_START -> kv -> {
                    final long keyStart = kv.key().getLong(0);
                    return ValLong.create(keyStart);
                };
                case RangedStateFields.KEY_END -> kv -> {
                    final long keyEnd = kv.key().getLong(Long.BYTES);
                    return ValLong.create(keyEnd);
                };
                case RangedStateFields.VALUE_TYPE -> kv -> {
                    final byte typeId = kv.val().get(0);
                    return ValUtil.getType(typeId);
                };
                case RangedStateFields.VALUE -> kv -> {
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
    public Key getKey(final KeyVal<ByteBuffer> keyVal) {
        final ByteBuffer byteBuffer = keyVal.key();
        final long keyStart = byteBuffer.getLong(0);
        final long keyEnd = byteBuffer.getLong(Long.BYTES);
        return new Key(keyStart, keyEnd);
    }

    @Override
    public StateValue getVal(final KeyVal<ByteBuffer> keyVal) {
        final ByteBuffer byteBuffer = keyVal.val();
        final byte typeId = byteBuffer.get(0);
        final int valueStart = Byte.BYTES;
        final ByteBuffer slice = byteBuffer.slice(valueStart, byteBuffer.limit() - valueStart);
        final byte[] valueBytes = ByteBufferUtils.toBytes(slice);
        return new StateValue(typeId, ByteBuffer.wrap(valueBytes));
    }
}
