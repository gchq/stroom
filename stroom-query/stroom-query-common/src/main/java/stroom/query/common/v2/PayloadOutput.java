package stroom.query.common.v2;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;

public class PayloadOutput extends Output {

    public PayloadOutput(final int bufferSize) {
        super(bufferSize, -1);
    }

    public void writeByteBuffer(final ByteBuffer byteBuffer) throws KryoException {
        final int count = byteBuffer.remaining();
        require(count);
        final int p = position;
        position += count;
        final byte[] buffer = this.buffer;
        byteBuffer.get(buffer, p, count);
    }
}
