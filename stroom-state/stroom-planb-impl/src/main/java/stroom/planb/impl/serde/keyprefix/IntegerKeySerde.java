package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

public class IntegerKeySerde extends SimpleKeyPrefixSerde {

    public IntegerKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Integer.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeInteger(val, byteBuffer);
    }
}
