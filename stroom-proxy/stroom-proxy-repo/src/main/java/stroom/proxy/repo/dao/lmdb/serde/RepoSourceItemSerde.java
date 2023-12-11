package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.dao.lmdb.RepoSourceItemPart;

import java.nio.ByteBuffer;

public class RepoSourceItemSerde implements Serde<RepoSourceItemPart> {

    @Override
    public PooledByteBuffer serialise(final RepoSourceItemPart value) {
        return ByteBuffers.write(output -> {
            output.writeLong(value.fileStoreId());
            output.writeLong(value.feedId());
            output.writeString(value.name());
            output.writeLong(value.aggregateId());
            output.writeLong(value.totalByteSize());
            output.writeString(value.extensions());
        });
    }

    @Override
    public RepoSourceItemPart deserialise(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
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
