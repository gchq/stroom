package stroom.pipeline.refdata.store;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A staging value held in byte form
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public class StagingValueImpl implements StagingValue {

    private final ByteBuffer fullByteBuffer;
    private final ByteBuffer valueByteBufferView;

    public StagingValueImpl(final ByteBuffer fullByteBuffer) {
        this.fullByteBuffer = Objects.requireNonNull(fullByteBuffer);
        this.valueByteBufferView = fullByteBuffer.slice(
                VALUE_OFFSET,
                fullByteBuffer.remaining() - StagingValue.META_LENGTH);
    }

    @Override
    public long getValueHashCode() {
        return StagingValueSerde.extractValueHash(fullByteBuffer);
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        return valueStoreHashAlgorithm.hash(valueByteBufferView);
    }

    public int getTypeId() {
        return StagingValueSerde.extractTypeId(fullByteBuffer);
    }

    @Override
    public boolean isNullValue() {
        return NullValue.TYPE_ID == getTypeId()
                || valueByteBufferView.remaining() == 0;
    }

    @Override
    public ByteBuffer getValueBuffer() {
        return valueByteBufferView;
    }

    @Override
    public ByteBuffer getFullByteBuffer() {
        return fullByteBuffer;
    }

    @Override
    public int size() {
        return fullByteBuffer.remaining();
    }

    /**
     * Copies the contents of this into a new {@link StagingValue} backed by the supplied buffer.
     */
    @Override
    public StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(getFullByteBuffer(), newByteBuffer);
        return new StagingValueImpl(newByteBuffer);
    }

    @Override
    public String toString() {
        return "StagingValueImpl{" +
                "typeId=" + getTypeId() +
                "valueHash=" + getValueHashCode() +
                "byteBuffer=" + ByteBufferUtils.byteBufferInfo(fullByteBuffer) +
                ", valueByteBufferView=" + ByteBufferUtils.byteBufferInfo(valueByteBufferView) +
                '}';
    }
}
