package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import java.nio.ByteBuffer;

public class ShortKeySerde extends SimpleKeyPrefixSerde {

    public ShortKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Short.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeShort(val, byteBuffer);
    }
}
