package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

public class DoubleKeySerde extends SimpleKeyPrefixSerde {

    public DoubleKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Double.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final double d = byteBuffer.getDouble();
        return ValDouble.create(d);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDouble(val, byteBuffer);
    }
}
