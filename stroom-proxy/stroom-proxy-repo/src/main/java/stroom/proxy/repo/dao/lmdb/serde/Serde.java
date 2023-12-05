package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public interface Serde<T> {

    ByteBuffer serialise(T value);

    T deserialise(ByteBuffer byteBuffer);
}
