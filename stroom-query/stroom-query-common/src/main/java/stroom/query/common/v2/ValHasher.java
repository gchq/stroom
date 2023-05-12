package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class ValHasher {
    private int bufferSize = 128;

    public long hash(final Val[] values) {
        if (values == null) {
            return -1;
        } else if (values.length == 0) {
            return 0;
        }
        try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize)) {
            ValSerialiser.writeArray(output, values);
            output.flush();
            final ByteBuffer buffer = output.getByteBuffer().flip();
            bufferSize = Math.max(bufferSize, buffer.capacity());
            return LongHashFunction.xx3().hashBytes(buffer);
        }
    }
}
