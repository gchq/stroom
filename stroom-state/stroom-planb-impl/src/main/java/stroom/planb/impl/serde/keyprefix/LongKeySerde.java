package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import java.nio.ByteBuffer;

public class LongKeySerde extends SimpleKeyPrefixSerde {

    public LongKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Long.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValLong.create(l);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeLong(val, byteBuffer);
    }
}
