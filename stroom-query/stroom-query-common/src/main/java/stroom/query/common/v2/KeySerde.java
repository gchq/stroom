package stroom.query.common.v2;

import stroom.lmdb.Serde;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteBuffer;

public class KeySerde implements Serde<Key> {
    private final ItemSerialiser itemSerialiser;

    public KeySerde(final ItemSerialiser itemSerialiser) {
        this.itemSerialiser = itemSerialiser;
    }

    @Override
    public Key deserialize(final ByteBuffer byteBuffer) {
//        try (final UnsafeMemoryInput input = new UnsafeMemoryInput()) {
//            input.setBuffer(byteBuffer);
//            return itemSerialiser.readKey(input);
//        }

        try (final UnsafeByteBufferInput input = new UnsafeByteBufferInput()) {
            input.setBuffer(byteBuffer);
//            input.setVariableLengthEncoding(false);
            return itemSerialiser.readKey(input);
        }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Key object) {
//        try (final UnsafeMemoryOutput output = new UnsafeMemoryOutput()) {
//            output.setBuffer(byteBuffer);
//            itemSerialiser.writeKey(object, output);
//        }

        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput()) {
            output.setBuffer(byteBuffer);
//            output.setVariableLengthEncoding(false);
            itemSerialiser.writeKey(object, output);
        }
    }
}
