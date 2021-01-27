package stroom.query.common.v2;

import stroom.util.io.ByteSizeUnit;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

public class KeySerde {
    private static final int MIN_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(4);
    private static final int MAX_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);

    private final ItemSerialiser itemSerialiser;

    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MIN_SIZE);

    public KeySerde(final ItemSerialiser itemSerialiser) {
        this.itemSerialiser = itemSerialiser;
    }

    public Key deserialize(final ByteBuffer byteBuffer) {
        try (final UnsafeByteBufferInput input = new UnsafeByteBufferInput()) {
            input.setBuffer(byteBuffer);
            return itemSerialiser.readKey(input);
        }
    }

    public ByteBuffer serialize(final Key object) {
        byteBuffer.clear();
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput()) {
            output.setBuffer(byteBuffer, MAX_SIZE);
            itemSerialiser.writeKey(object, output);
            byteBuffer = output.getByteBuffer();
        }
        byteBuffer.flip();
        return byteBuffer;
    }
}
