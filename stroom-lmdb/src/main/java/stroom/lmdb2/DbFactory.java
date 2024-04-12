package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb2.serde2.ExtendedSerde;

import jakarta.inject.Inject;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;

public class DbFactory {

    private final ByteBufferPool byteBufferPool;

    @Inject
    public DbFactory(final ByteBufferPool byteBufferPool) {
        this.byteBufferPool = byteBufferPool;
    }

    public <K, V> Db<K, V> openDb(final Dbi<ByteBuffer> dbi,
                                  final LmdbWriteQueue writeQueue,
                                  final ExtendedSerde<K> keySerde,
                                  final ExtendedSerde<V> valueSerde) {
        return new Db<>(dbi, writeQueue, byteBufferPool, keySerde, valueSerde);
    }
}
