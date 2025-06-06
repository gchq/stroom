package stroom.planb.impl.db.histogram;

import stroom.lmdb.serde.UnsignedBytes;
import stroom.planb.impl.serde.count.CountSerde;

import java.nio.ByteBuffer;

public class HistogramCountSerde implements CountSerde<Long> {

    private final UnsignedBytes unsignedBytes;

    public HistogramCountSerde(final UnsignedBytes unsignedBytes) {
        this.unsignedBytes = unsignedBytes;
    }

    private void put(final ByteBuffer byteBuffer, final long value) {
        unsignedBytes.put(byteBuffer, Math.min(unsignedBytes.maxValue(), value));
    }

    @Override
    public void put(final ByteBuffer byteBuffer, final int position, final long value) {
        byteBuffer.position(position);
        put(byteBuffer, value);
    }

    @Override
    public void add(final ByteBuffer byteBuffer, final int position, final long value) {
        byteBuffer.position(position);
        final long currentValue = unsignedBytes.get(byteBuffer);
        final long newValue = currentValue + value;
        byteBuffer.position(position);
        put(byteBuffer, newValue);
    }

    @Override
    public long getVal(final ByteBuffer byteBuffer) {
        return get(byteBuffer);
    }

    @Override
    public Long get(final ByteBuffer byteBuffer) {
        return unsignedBytes.get(byteBuffer);
    }

    public void merge(final ByteBuffer buffer1,
                      final ByteBuffer buffer2,
                      final ByteBuffer output) {
        final long value1 = unsignedBytes.get(buffer1);
        final long value2 = unsignedBytes.get(buffer2);
        final long total = value1 + value2;
        put(output, total);
    }

    @Override
    public int length() {
        return unsignedBytes.length();
    }
}
