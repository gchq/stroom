package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.FeedKey;

import java.nio.ByteBuffer;

public class FeedKeySerde implements Serde<FeedKey> {

    @Override
    public PooledByteBuffer serialise(final FeedKey value) {
        return ByteBuffers.write(output -> {
            output.writeString(value.feed());
            output.writeString(value.type());
        });
    }

    @Override
    public FeedKey deserialise(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
            final String feed = input.readString();
            final String type = input.readString();
            return new FeedKey(feed, type);
        });
    }
}
