package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

class MigrationValue implements StagingValue {

    private final int typeId;
    private final ByteBuffer valueBuffer;
    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    private volatile Long valueHash = null;

    MigrationValue(final int typeId,
                   final ByteBuffer valueBuffer,
                   final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        this(typeId, valueBuffer, valueStoreHashAlgorithm, null);
    }

    private MigrationValue(final int typeId,
                           final ByteBuffer valueBuffer,
                           final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                           final Long valueHash) {
        this.typeId = typeId;
        this.valueBuffer = valueBuffer;
        this.valueStoreHashAlgorithm = valueStoreHashAlgorithm;
        this.valueHash = valueHash;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        Objects.requireNonNull(valueStoreHashAlgorithm);
        if (valueStoreHashAlgorithm.getClass().equals(this.valueStoreHashAlgorithm.getClass())) {
            return getValueHashCode();
        } else {
            return valueStoreHashAlgorithm.hash(valueBuffer);
        }
    }

    @Override
    public boolean isNullValue() {
        return false;
    }

    @Override
    public long getValueHashCode() {
        if (valueHash == null) {
            valueHash = valueStoreHashAlgorithm.hash(valueBuffer);
        }
        return valueHash;
    }

    @Override
    public int getTypeId() {
        return typeId;
    }

    @Override
    public int size() {
        return valueBuffer.remaining();
    }

    @Override
    public ByteBuffer getValueBuffer() {
        return valueBuffer;
    }

    @Override
    public ByteBuffer getFullByteBuffer() {
        return valueBuffer;
    }

    @Override
    public StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(getFullByteBuffer(), newByteBuffer);
        return new MigrationValue(typeId, newByteBuffer, valueStoreHashAlgorithm, valueHash);
    }
}
