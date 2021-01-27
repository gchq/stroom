package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.util.io.ByteSizeUnit;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

public class ValueSerde {
    private static final int MIN_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(4);
    private static final int MAX_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);

    private final ItemSerialiser itemSerialiser;

    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MIN_SIZE);

    public ValueSerde(final ItemSerialiser itemSerialiser) {
        this.itemSerialiser = itemSerialiser;
    }

    public Generator[] deserialize(final ByteBuffer byteBuffer) {
        try (final UnsafeByteBufferInput input = new UnsafeByteBufferInput()) {
            input.setBuffer(byteBuffer);
            return itemSerialiser.readGenerators(input);
        }
    }

    public ByteBuffer serialize(final Generator[] object) {
        byteBuffer.clear();
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput()) {
            output.setBuffer(byteBuffer, MAX_SIZE);
            itemSerialiser.writeGenerators(object, output);
            byteBuffer = output.getByteBuffer();
        }
        byteBuffer.flip();
        return byteBuffer;
    }
}
