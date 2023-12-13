package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.FeedKey;

import jakarta.inject.Inject;

import java.nio.ByteBuffer;

public class FeedKeySerde implements Serde<FeedKey> {

    private final ByteBuffers byteBuffers;

    @Inject
    FeedKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final FeedKey object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final FeedKey value) {
        return byteBuffers.write(output -> {
            output.writeString(value.feed());
            output.writeString(value.type());
        });
    }

    @Override
    public FeedKey deserialize(final ByteBuffer byteBuffer) {
        return byteBuffers.read(byteBuffer, input -> {
            final String feed = input.readString();
            final String type = input.readString();
            return new FeedKey(feed, type);
        });
    }
}
