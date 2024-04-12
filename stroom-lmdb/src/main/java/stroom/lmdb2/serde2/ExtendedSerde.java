package stroom.lmdb2.serde2;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;

public interface ExtendedSerde<T> extends Serde<T> {

    PooledByteBuffer serialize(T value, ByteBufferPool byteBufferPool);
}
