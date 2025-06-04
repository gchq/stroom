package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import java.nio.ByteBuffer;

public class LongValSerde extends SimpleValSerde {

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValLong.create(l);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeLong(val, byteBuffer);
    }

    @Override
    int size() {
        return Long.BYTES;
    }
}
