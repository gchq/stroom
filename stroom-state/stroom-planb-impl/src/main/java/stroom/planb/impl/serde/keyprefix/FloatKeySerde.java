package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import java.nio.ByteBuffer;

public class FloatKeySerde extends SimpleKeyPrefixSerde {

    public FloatKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Float.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeFloat(val, byteBuffer);
    }
}
