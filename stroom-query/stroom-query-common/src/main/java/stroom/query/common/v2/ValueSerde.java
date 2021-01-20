package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.lmdb.Serde;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

public class ValueSerde implements Serde<Generator[]> {
    private final ItemSerialiser itemSerialiser;

    public ValueSerde(final ItemSerialiser itemSerialiser) {
        this.itemSerialiser = itemSerialiser;
    }

    @Override
    public Generator[] deserialize(final ByteBuffer byteBuffer) {
        try (final UnsafeByteBufferInput input = new UnsafeByteBufferInput()) {
            input.setBuffer(byteBuffer);
//            input.setVariableLengthEncoding(false);
            return itemSerialiser.readGenerators(input);
        }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Generator[] object) {
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput()) {
            output.setBuffer(byteBuffer);
//            output.setVariableLengthEncoding(false);
            itemSerialiser.writeGenerators(object, output);
        }
    }
}
