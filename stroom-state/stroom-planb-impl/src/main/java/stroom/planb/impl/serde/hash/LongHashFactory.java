package stroom.planb.impl.serde.hash;

import stroom.bytebuffer.ByteBufferUtils;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class LongHashFactory implements HashFactory {

    @Override
    public Hash create(final byte[] bytes) {
        return new LongHash(LongHashFunction.xx3().hashBytes(bytes));
    }

    @Override
    public Hash create(final ByteBuffer byteBuffer) {
        return create(ByteBufferUtils.toBytes(byteBuffer));
    }

    @Override
    public int hashLength() {
        return Long.BYTES;
    }

    private static class LongHash implements Hash {
        private final long hash;

        public LongHash(final long hash) {
            this.hash = hash;
        }

        @Override
        public void write(final ByteBuffer byteBuffer) {
            byteBuffer.putLong(hash);
        }

        @Override
        public int len() {
            return Long.BYTES;
        }
    }
}
