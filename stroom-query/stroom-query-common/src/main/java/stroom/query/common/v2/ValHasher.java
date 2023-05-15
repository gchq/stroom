package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class ValHasher {

    public static long hash(final Val[] values) {
        if (values == null) {
            return -1;
        } else if (values.length == 0) {
            return 0;
        }
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput(1024, -1)) {
            for (final Val val : values) {
                output.writeByte(val.type().getId());
                switch (val.type()) {
                    case NULL -> output.writeBoolean(false);
                    case BOOLEAN -> output.writeBoolean(val.toBoolean());
                    case FLOAT, DOUBLE -> output.writeDouble(val.toDouble());
                    case INTEGER, LONG, DATE -> output.writeLong(val.toLong());
                    case STRING, ERR -> output.writeString(val.toString());
                    default -> throw new IllegalStateException("Unexpected value: " + val.type());
                }
            }
            output.flush();
            final ByteBuffer buffer = output.getByteBuffer().flip();
            return LongHashFunction.xx3().hashBytes(buffer);
        }
    }
}
