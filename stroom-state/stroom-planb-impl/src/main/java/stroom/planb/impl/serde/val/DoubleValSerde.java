package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

public class DoubleValSerde extends SimpleValSerde {

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final double d = byteBuffer.getDouble();
        return ValDouble.create(d);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeDouble(val, byteBuffer);
    }

    @Override
    int size() {
        return Double.BYTES;
    }
}
