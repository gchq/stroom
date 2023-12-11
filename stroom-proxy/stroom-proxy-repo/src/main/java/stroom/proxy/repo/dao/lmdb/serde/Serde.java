package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public interface Serde<T> {

    PooledByteBuffer serialise(T value);

    T deserialise(ByteBuffer byteBuffer);
}
