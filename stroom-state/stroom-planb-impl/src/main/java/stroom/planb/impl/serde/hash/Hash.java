package stroom.planb.impl.serde.hash;

import java.nio.ByteBuffer;

public interface Hash {

    void write(ByteBuffer byteBuffer);

    int len();

}
