package stroom.lmdb.topic;

import stroom.lmdb.serde.Serde;

import java.nio.ByteBuffer;

class VoidSerde implements Serde<Void> {

    VoidSerde() {
        super();
    }

    @Override
    public Void deserialize(final ByteBuffer byteBuffer) {
        byteBuffer.rewind();
        return null;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Void object) {
        // Nothing to serialise
        byteBuffer.flip();
    }
}
