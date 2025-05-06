package stroom.planb.impl.db.hash;

import java.nio.ByteBuffer;

public interface HashFactory {

    Hash create(byte[] bytes);

    Hash create(ByteBuffer byteBuffer);

    int hashLength();
}
