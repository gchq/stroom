package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;

import java.nio.ByteBuffer;

public class BooleanKeySerde extends SimpleKeyPrefixSerde {

    public BooleanKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Byte.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValBoolean.create(b != 0);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeBoolean(val, byteBuffer);
    }
}
