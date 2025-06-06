package stroom.planb.impl.serde.hash;

import java.nio.ByteBuffer;

public interface HashFactory {

    Hash create(byte[] bytes);

    Hash create(ByteBuffer byteBuffer);

    int hashLength();
}
