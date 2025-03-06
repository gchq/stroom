package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferUtils;

import org.lmdbjava.CursorIterable.KeyVal;

import java.nio.ByteBuffer;

public class BBKV extends KV<ByteBuffer, ByteBuffer> {

    public BBKV(final ByteBuffer key, final ByteBuffer value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "BBKV{" +
               "key=" + ByteBufferUtils.byteBufferInfo(key()) +
               ", value=" + ByteBufferUtils.byteBufferInfo(val()) +
               '}';
    }

    public static BBKV create(final KeyVal<ByteBuffer> keyVal) {
        return new BBKV(keyVal.key(), keyVal.val());
    }
}
