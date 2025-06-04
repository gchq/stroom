package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import java.nio.ByteBuffer;

public class FloatValSerde extends SimpleValSerde {

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeFloat(val, byteBuffer);
    }

    @Override
    int size() {
        return Float.BYTES;
    }
}
