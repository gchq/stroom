package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.BBKV;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * KEY =   <KEY_HASH><SESSION_START><SESSION_END>
 * VALUE = <KEY_BYTES>
 */
public class SessionSerde implements Serde<Session, Session> {

    private static final int KEY_LENGTH = Long.BYTES + Long.BYTES + Long.BYTES;

    private final ByteBufferFactory byteBufferFactory;

    public SessionSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    public <T> T createKeyByteBuffer(final Session key, final Function<ByteBuffer, T> function) {
        // Hash the key.
        final long keyHash = LongHashFunction.xx3().hashBytes(key.getKey());
        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
        try {
            keyByteBuffer.putLong(keyHash);
            keyByteBuffer.putLong(key.getStart());
            keyByteBuffer.putLong(key.getEnd());
            keyByteBuffer.flip();
            return function.apply(keyByteBuffer);
        } finally {
            byteBufferFactory.release(keyByteBuffer);
        }
    }

    @Override
    public <R> R createValueByteBuffer(final Session key,
                                       final Session value,
                                       final Function<ByteBuffer, R> function) {
        final ByteBuffer valueByteBuffer = byteBufferFactory.acquire(key.getKey().length);
        try {
            valueByteBuffer.put(key.getKey());
            valueByteBuffer.flip();
            return function.apply(valueByteBuffer);
        } finally {
            byteBufferFactory.release(valueByteBuffer);
        }
    }

    @Override
    public <R> R createPrefixPredicate(final Session key, final Function<Predicate<BBKV>, R> function) {
        final ByteBuffer prefixByteBuffer = byteBufferFactory.acquire(key.getKey().length);
        try {
            prefixByteBuffer.put(key.getKey());
            prefixByteBuffer.flip();

            return function.apply(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), prefixByteBuffer));
        } finally {
            byteBufferFactory.release(prefixByteBuffer);
        }
    }

    @Override
    public void createPrefixPredicate(final BBKV kv, final Consumer<Predicate<BBKV>> consumer) {
        final ByteBuffer slice = kv.val().duplicate();
        consumer.accept(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), slice));
    }

    @Override
    public boolean hasPrefix() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Function<KeyVal<ByteBuffer>, Val>[] getValExtractors(final FieldIndex fieldIndex) {
        final Function<KeyVal<ByteBuffer>, Val>[] functions = new Function[fieldIndex.size()];
        for (int i = 0; i < fieldIndex.getFields().length; i++) {
            final String field = fieldIndex.getField(i);
            functions[i] = switch (field) {
                case SessionFields.KEY -> kv -> ValUtil.getValue((byte) 0, kv.val().duplicate());
                case SessionFields.START -> kv -> {
                    final long start = kv.key().getLong(Long.BYTES);
                    return ValDate.create(start);
                };
                case SessionFields.END -> kv -> {
                    final long end = kv.key().getLong(Long.BYTES + Long.BYTES);
                    return ValDate.create(end);
                };
//                case SessionFields.TERMINAL -> kv -> {
//                    final byte typeId = kv.val().get(0);
//                    final int valueStart = Byte.BYTES;
//                    return ValUtil.getValue(typeId, kv.val().slice(valueStart, kv.val().limit() - valueStart));
//                };
                default -> byteBuffer -> ValNull.INSTANCE;
            };
        }
        return functions;
    }

    @Override
    public Session getKey(final BBKV kv) {
        final long start = kv.key().getLong(Long.BYTES);
        final long end = kv.key().getLong(Long.BYTES + Long.BYTES);
        final byte[] key = ByteBufferUtils.toBytes(kv.val());
        return new Session(key, start, end);
    }

    @Override
    public Session getVal(final BBKV kv) {
        return getKey(kv);
    }

    @Override
    public int getKeyLength() {
        return KEY_LENGTH;
    }
}
