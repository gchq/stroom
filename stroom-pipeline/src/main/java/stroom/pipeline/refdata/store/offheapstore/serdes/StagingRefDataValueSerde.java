package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.bytebuffer.hbase.Bytes;
import stroom.lmdb.serde.Deserializer;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.Serializer;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.StagingRefDataValue;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public class StagingRefDataValueSerde
        implements Serde<StagingRefDataValue>,
        Serializer<StagingRefDataValue>,
        Deserializer<StagingRefDataValue> {

    private static final int TYPE_ID_OFFSET = 0;
    private static final int VALUE_HASH_OFFSET = TYPE_ID_OFFSET + Byte.BYTES;
    private static final int VALUE_OFFSET = VALUE_HASH_OFFSET + Long.BYTES;

    private final GenericRefDataValueSerde genericRefDataValueSerde;
    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm;

    @Inject
    public StagingRefDataValueSerde(final GenericRefDataValueSerde genericRefDataValueSerde,
                                    final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        this.genericRefDataValueSerde = genericRefDataValueSerde;
        this.valueStoreHashAlgorithm = valueStoreHashAlgorithm;
    }

    @Override
    public StagingRefDataValue deserialize(final ByteBuffer byteBuffer) {
        final byte typeId = byteBuffer.get();
        final long valueHash = byteBuffer.getLong();
        // Need to hide the type/hash bit from the genericRefDataValueSerde so slice off the rest of the buffer
        final ByteBuffer valueBuffer = byteBuffer.slice();
        // Now we can rewind the original buffer
        byteBuffer.rewind();
        final RefDataValue refDataValue = genericRefDataValueSerde.deserialize(valueBuffer, typeId);
        // To save having to re-compute the hash later, set it on the value
//        refDataValue.setValueHashCode(valueHash);
        return new StagingRefDataValue(typeId, refDataValue);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final StagingRefDataValue stagingRefDataValue) {
        final RefDataValue refDataValue = stagingRefDataValue.getRefDataValue();
        byteBuffer.put(stagingRefDataValue.getTypeId());
        byteBuffer.putLong(refDataValue.getValueHashCode(valueStoreHashAlgorithm));
        genericRefDataValueSerde.serialize(byteBuffer, refDataValue);
    }

    @Override
    public ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                final StagingRefDataValue stagingRefDataValue) {
        final RefDataValue refDataValue = stagingRefDataValue.getRefDataValue();

        try {
            pooledByteBufferOutputStream.write(stagingRefDataValue.getTypeId());
            pooledByteBufferOutputStream.write(Bytes.toBytes(refDataValue.getValueHashCode(valueStoreHashAlgorithm)));
            final ByteBuffer refDataValueBuffer = genericRefDataValueSerde.serialize(
                    pooledByteBufferOutputStream, refDataValue);

            pooledByteBufferOutputStream.write(refDataValueBuffer);
            return pooledByteBufferOutputStream.getByteBuffer();
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Unable to write value {} to output stream: {}",
                    stagingRefDataValue, e.getMessage()), e);
        }
    }

    public byte extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    public long extractValueHash(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong(VALUE_HASH_OFFSET);
    }
}
