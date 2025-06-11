package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;

import java.nio.ByteBuffer;

public class DateKeySerde extends SimpleKeyPrefixSerde {

    public DateKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Long.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValDate.create(l);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDate(val, byteBuffer);
    }
}
