package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import java.nio.ByteBuffer;

public class ByteKeySerde extends SimpleKeyPrefixSerde {

    public ByteKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Byte.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeByte(val, byteBuffer);
    }
}
