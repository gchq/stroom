package stroom.planb.impl.db.hash;

import java.nio.ByteBuffer;

public interface Hash {

    void write(ByteBuffer byteBuffer);

    int len();

}
