package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class StringListSerde implements ExtendedSerde<List<String>> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final List<String> object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final List<String> list,
                                      final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, output -> {
            output.writeInt(list.size());
            for (final String string : list) {
                output.writeString(string);
            }
        });
    }

    @Override
    public List<String> deserialize(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
            final int size = input.readInt();
            final List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(input.readString());
            }
            return list;
        });
    }
}
