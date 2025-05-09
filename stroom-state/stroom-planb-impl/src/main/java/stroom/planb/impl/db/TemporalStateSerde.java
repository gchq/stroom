package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.TemporalState.Key;
import stroom.planb.impl.db.serde.ValSerdeUtil;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * KEY =   <KEY_HASH><EFFECTIVE_TIME>
 * VALUE = <KEY_LENGTH><KEY_BYTES><VALUE_TYPE><VALUE_BYTES>
 */
public class TemporalStateSerde implements Serde<Key, Val> {

    private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

    private final ByteBuffers byteBuffers;

    public TemporalStateSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public <T> T createKeyByteBuffer(final Key key, final Function<ByteBuffer, T> function) {
        return byteBuffers.use(KEY_LENGTH, keyByteBuffer -> {
            // Hash the key.
            final long keyHash = LongHashFunction.xx3().hashBytes(key.getBytes());
            keyByteBuffer.putLong(keyHash);
            keyByteBuffer.putLong(key.getEffectiveTime());
            keyByteBuffer.flip();
            return function.apply(keyByteBuffer);
        });
    }

    @Override
    public <T> T createValueByteBuffer(final Key key,
                                       final Val value,
                                       final Function<ByteBuffer, T> function) {
        return ValSerdeUtil.write(value, byteBuffers, function);
    }

    @Override
    public <R> R createPrefixPredicate(final Key key, final Function<Predicate<BBKV>, R> function) {
        return byteBuffers.use(Integer.BYTES + key.getBytes().length, prefixByteBuffer -> {
            putPrefix(prefixByteBuffer, key.getBytes());
            prefixByteBuffer.flip();
            return function.apply(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), prefixByteBuffer));
        });
    }

    @Override
    public void createPrefixPredicate(final BBKV kv, final Consumer<Predicate<BBKV>> consumer) {
        final int keyLength = kv.val().getInt(0);
        final ByteBuffer slice = kv.val().slice(0, Integer.BYTES + keyLength);
        consumer.accept(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), slice));
    }

    @Override
    public boolean hasPrefix() {
        return true;
    }

    private void putPrefix(final ByteBuffer byteBuffer, final byte[] keyBytes) {
        byteBuffer.putInt(keyBytes.length);
        byteBuffer.put(keyBytes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(final FieldIndex fieldIndex) {
        final Function<KeyVal<ByteBuffer>, Val>[] functions = new Function[fieldIndex.size()];
        for (int i = 0; i < fieldIndex.getFields().length; i++) {
            final String field = fieldIndex.getField(i);
            functions[i] = switch (field) {
                case TemporalStateFields.KEY -> kv -> {
                    final int keyLength = kv.val().getInt(0);
                    return ValString.create(ByteBufferUtils.toString(kv.val().slice(Integer.BYTES, keyLength)));
                };
                case TemporalStateFields.EFFECTIVE_TIME -> kv -> {
                    final long effectiveTime = kv.key().getLong(Long.BYTES);
                    return ValDate.create(effectiveTime);
                };
                case TemporalStateFields.VALUE_TYPE -> kv -> {
                    final int keyLength = kv.val().getInt(0);
                    final int valueStart = Integer.BYTES + keyLength;
                    return ValString.create(ValSerdeUtil
                            .read(kv.val().slice(valueStart, kv.val().limit() - valueStart)).type()
                            .toString());
                };
                case TemporalStateFields.VALUE -> kv -> {
                    final int keyLength = kv.val().getInt(0);
                    final int valueStart = Integer.BYTES + keyLength;
                    return ValSerdeUtil.read(kv.val().slice(valueStart, kv.val().limit() - valueStart));
                };
                default -> byteBuffer -> ValNull.INSTANCE;
            };
        }
        return functions;
    }

    @Override
    public Key getKey(final BBKV kv) {
        final ByteBuffer byteBuffer = kv.val();
        final int keyLength = byteBuffer.getInt(0);
        final ByteBuffer slice = byteBuffer.slice(Integer.BYTES, keyLength);
        final byte[] keyBytes = ByteBufferUtils.toBytes(slice);
        final long effectiveTime = kv.key().getLong(Long.BYTES);
        return new Key(keyBytes, effectiveTime);
    }

    @Override
    public Val getVal(final BBKV kv) {
        final ByteBuffer byteBuffer = kv.val();
        final int keyLength = byteBuffer.getInt(0);
        final int valueStart = Integer.BYTES + keyLength;
        final ByteBuffer slice = byteBuffer.slice(valueStart, byteBuffer.limit() - valueStart);
        return ValSerdeUtil.read(slice);
    }

    @Override
    public int getKeyLength() {
        return KEY_LENGTH;
    }
}
