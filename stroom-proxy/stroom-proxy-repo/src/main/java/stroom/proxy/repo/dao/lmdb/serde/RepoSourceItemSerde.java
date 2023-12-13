package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.RepoSourceItemPart;

import jakarta.inject.Inject;

import java.nio.ByteBuffer;

public class RepoSourceItemSerde implements Serde<RepoSourceItemPart> {

    private final ByteBuffers byteBuffers;

    @Inject
    RepoSourceItemSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RepoSourceItemPart object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final RepoSourceItemPart value) {
        return byteBuffers.write(output -> {
            output.writeLong(value.fileStoreId());
            output.writeLong(value.feedId());
            output.writeString(value.name());
            output.writeLong(value.aggregateId());
            output.writeLong(value.totalByteSize());
            output.writeString(value.extensions());
        });
    }

    @Override
    public RepoSourceItemPart deserialize(final ByteBuffer byteBuffer) {
        return byteBuffers.read(byteBuffer, input -> {
            final long fileStoreId = input.readLong();
            final long feedId = input.readLong();
            final String name = input.readString();
            final long aggregateId = input.readLong();
            final long totalByteSize = input.readLong();
            final String extension = input.readString();
            return new RepoSourceItemPart(fileStoreId, feedId, name, aggregateId, totalByteSize, extension);
        });
    }
}
