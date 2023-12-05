package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.dao.lmdb.MyByteBufferOutput;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;

public class FeedKeySerde implements Serde<FeedKey> {

    @Override
    public ByteBuffer serialise(final FeedKey value) {
        final ByteBuffer byteBuffer;
        try (final MyByteBufferOutput output = new MyByteBufferOutput(100, -1)) {
            output.writeString(value.feed());
            output.writeString(value.type());
            output.flush();
            byteBuffer = output.getByteBuffer().flip();
        }
        return byteBuffer;
    }

    @Override
    public FeedKey deserialise(final ByteBuffer byteBuffer) {
        try (final Input input = new UnsafeByteBufferInput(byteBuffer.duplicate())) {
            final String feed = input.readString();
            final String type = input.readString();
            return new FeedKey(feed, type);
        }
    }
}
