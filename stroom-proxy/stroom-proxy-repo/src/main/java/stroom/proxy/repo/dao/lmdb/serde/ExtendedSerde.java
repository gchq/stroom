package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;

public interface ExtendedSerde<T> extends Serde<T> {

    PooledByteBuffer serialize(T value, ByteBufferPool byteBufferPool);
}
