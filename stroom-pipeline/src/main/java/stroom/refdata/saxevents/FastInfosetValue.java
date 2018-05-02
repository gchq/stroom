package stroom.refdata.saxevents;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FastInfosetValue extends EventListValue {

    static final short TYPE_ID = 1;

    private final byte[] fastInfosetBytes;

    public FastInfosetValue(final byte[] fastInfosetBytes) {
        this.fastInfosetBytes = fastInfosetBytes;
    }

    static FastInfosetValue of(byte[] fastInfosetBytes) {
        return new FastInfosetValue(fastInfosetBytes);
    }

    static FastInfosetValue fromByteBuffer(final ByteBuffer byteBuffer) {

        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return new FastInfosetValue(bytes);
    }

    @Override
    public short getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FastInfosetValue that = (FastInfosetValue) o;
        return Arrays.equals(fastInfosetBytes, that.fastInfosetBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(fastInfosetBytes);
    }

    @Override
    public byte[] getValueBytes() {
        return fastInfosetBytes;
    }
}
