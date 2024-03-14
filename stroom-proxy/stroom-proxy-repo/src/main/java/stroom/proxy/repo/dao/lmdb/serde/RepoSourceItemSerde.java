package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.RepoSourceItemPart;

import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;

public class RepoSourceItemSerde implements ExtendedSerde<RepoSourceItemPart> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RepoSourceItemPart object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final RepoSourceItemPart value, final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, output -> {
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
