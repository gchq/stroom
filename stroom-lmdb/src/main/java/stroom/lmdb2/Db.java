package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb2.serde2.ExtendedSerde;
import stroom.util.shared.Clearable;

import jakarta.inject.Provider;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class Db<K, V> implements Clearable {

    private final Dbi<ByteBuffer> dbi;
    private final LmdbWriteQueue writeQueue;
    private final ByteBufferPool byteBufferPool;
    private final ExtendedSerde<K> keySerde;
    private final ExtendedSerde<V> valueSerde;

    public Db(final Dbi<ByteBuffer> dbi,
              final LmdbWriteQueue writeQueue,
              final ByteBufferPool byteBufferPool,
              final ExtendedSerde<K> keySerde,
              final ExtendedSerde<V> valueSerde) {
        this.dbi = dbi;
        this.writeQueue = writeQueue;
        this.byteBufferPool = byteBufferPool;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public V get(final K key) {
        final PooledByteBuffer keyByteBuffer = serializeKey(key);
        return writeQueue.writeResult(txn -> {
            final ByteBuffer byteBuffer = dbi.get(txn.get(), keyByteBuffer.getByteBuffer());
            keyByteBuffer.close();
            if (byteBuffer == null) {
                return null;
            }
            return deserializeValue(byteBuffer);
        });
    }

    public Optional<V> getOptional(final K key) {
        return Optional.ofNullable(get(key));
    }

    public void getAll(final BiConsumer<K, V> consumer) {
        writeQueue.write(txn -> {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn.get())) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    final K key = deserializeKey(kv.key());
                    final V value = deserializeValue(kv.val());
                    consumer.accept(key, value);
                }
            }
        });
    }

    public void put(final K key, final V value) {
        final PooledByteBuffer keyByteBuffer = serializeKey(key);
        final PooledByteBuffer valueByteBuffer = serializeValue(value);
        writeQueue.write(txn -> {
            try {
                dbi.put(txn.get(), keyByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
            } finally {
                keyByteBuffer.close();
                valueByteBuffer.close();
            }
        });
    }

    public void putAsync(final K key, final V value) {
        final PooledByteBuffer keyByteBuffer = serializeKey(key);
        final PooledByteBuffer valueByteBuffer = serializeValue(value);
        putAsync(() -> keyByteBuffer, () -> valueByteBuffer);
    }

    public void putAsync(final Provider<PooledByteBuffer> keyProvider,
                         final Provider<PooledByteBuffer> valueProvider) {
        writeQueue.writeAsync(txn -> {
            final PooledByteBuffer keyByteBuffer = keyProvider.get();
            final PooledByteBuffer valueByteBuffer = valueProvider.get();
            try {
                dbi.put(txn.get(), keyByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
            } finally {
                keyByteBuffer.close();
                valueByteBuffer.close();
            }
        });
    }

    public void delete(final K key) {
        final PooledByteBuffer keyByteBuffer = serializeKey(key);
        writeQueue.write(txn -> {
            try {
                dbi.delete(txn.get(), keyByteBuffer.getByteBuffer());
            } finally {
                keyByteBuffer.close();
            }
        });
    }

    public void delete(final KeyRangeType type, final K start, final K stop) {
        final PooledByteBuffer startByteBuffer = serializeKey(start);
        final PooledByteBuffer stopByteBuffer = serializeKey(stop);
        final KeyRange<ByteBuffer> keyRange = new KeyRange<>(
                type,
                startByteBuffer.getByteBuffer(),
                stopByteBuffer.getByteBuffer());
        writeQueue.write(txn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn.get(), keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            } finally {
                startByteBuffer.close();
                stopByteBuffer.close();
            }
        });
    }

    public <R> R scan(final KeyRangeType type,
                      final K start,
                      final K stop,
                      final BiFunction<K, V, R> function) {
        final PooledByteBuffer startByteBuffer = serializeKey(start);
        final PooledByteBuffer stopByteBuffer = serializeKey(stop);
        final KeyRange<ByteBuffer> keyRange = new KeyRange<>(
                type,
                startByteBuffer.getByteBuffer(),
                stopByteBuffer.getByteBuffer());
        try {
            return scan(keyRange, function);
        } finally {
            startByteBuffer.close();
            stopByteBuffer.close();
        }
    }

    public <R> R scan(final KeyRange<ByteBuffer> keyRange,
                      final BiFunction<K, V, R> function) {
        return writeQueue.writeResult(txn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn.get(), keyRange)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final K key = deserializeKey(keyVal.key());
                    final V value = deserializeValue(keyVal.val());
                    final R result = function.apply(key, value);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        });
    }

//    public void iterate(final KeyRange<ByteBuffer> keyRange,
//                        final BiConsumer<K, V> consumer) {
//        writeQueue.write(txn -> {
//            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, keyRange)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
//                    final K key = deserializeKey(keyVal.key());
//                    final V value = deserializeValue(keyVal.val());
//                    consumer.accept(key, value);
//                }
//            }
//        });
//    }

    public PooledByteBuffer serializeKey(final K key) {
        return keySerde.serialize(key, byteBufferPool);
    }

    public K deserializeKey(final ByteBuffer byteBuffer) {
        return keySerde.deserialize(byteBuffer);
    }

    public PooledByteBuffer serializeValue(final V value) {
        return valueSerde.serialize(value, byteBufferPool);
    }

    public V deserializeValue(final ByteBuffer byteBuffer) {
        return valueSerde.deserialize(byteBuffer);
    }

    public Optional<K> getMinKey() {
        KeyRange<ByteBuffer> keyRange = KeyRange.all();
        return getNextKey(keyRange);
    }

    public Optional<K> getMaxKey() {
        KeyRange<ByteBuffer> keyRange = KeyRange.allBackward();
        return getNextKey(keyRange);
    }

    public Optional<K> getNextKey(final KeyRange<ByteBuffer> keyRange) {
        return writeQueue.writeResult(txn -> {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn.get(), keyRange)) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    return Optional.of(deserializeKey(kv.key()));
                }
            }
            return Optional.empty();
        });
    }

    public long count() {
        return writeQueue.writeResult(txn -> dbi.stat(txn.get()).entries);
    }

    @Override
    public void clear() {
        writeQueue.write(txn -> dbi.drop(txn.get(), false));
    }

    public void close() {
        dbi.close();
    }
}
