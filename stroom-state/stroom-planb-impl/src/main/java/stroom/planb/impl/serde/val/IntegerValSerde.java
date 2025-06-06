package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

public class IntegerValSerde extends SimpleValSerde {

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeInteger(val, byteBuffer);
    }

    @Override
    int size() {
        return Integer.BYTES;
    }
}
