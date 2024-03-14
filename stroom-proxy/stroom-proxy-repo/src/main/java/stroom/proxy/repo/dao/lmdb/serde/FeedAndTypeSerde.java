package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.FeedAndType;

import java.nio.ByteBuffer;

public class FeedAndTypeSerde implements ExtendedSerde<FeedAndType> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final FeedAndType object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final FeedAndType value, final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, output -> {
            output.writeString(value.feed());
            output.writeString(value.type());
        });
    }

    @Override
    public FeedAndType deserialize(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
            final String feed = input.readString();
            final String type = input.readString();
            return new FeedAndType(feed, type);
        });
    }
}
