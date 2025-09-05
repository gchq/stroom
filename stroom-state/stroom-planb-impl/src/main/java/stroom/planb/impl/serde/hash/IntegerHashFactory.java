package stroom.planb.impl.serde.hash;

import stroom.bytebuffer.ByteBufferUtils;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class IntegerHashFactory implements HashFactory {

    @Override
    public Hash create(final byte[] bytes) {
        return new IntegerHash(Long.hashCode(LongHashFunction.xx3().hashBytes(bytes)));
    }

    @Override
    public Hash create(final ByteBuffer byteBuffer) {
        return create(ByteBufferUtils.toBytes(byteBuffer));
    }

    @Override
    public int hashLength() {
        return Integer.BYTES;
    }

    private static class IntegerHash implements Hash {

        private final int hash;

        public IntegerHash(final int hash) {
            this.hash = hash;
        }

        @Override
        public void write(final ByteBuffer byteBuffer) {
            byteBuffer.putInt(hash);
        }

        @Override
        public int len() {
            return Integer.BYTES;
        }

        @Override
        public String toString() {
            return Integer.toString(hash);
        }
    }
}
