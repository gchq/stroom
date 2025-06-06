package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

public class DoubleValTimeSerde extends SimpleValTimeSerde {

    public DoubleValTimeSerde(final TimeSerde timeSerde) {
        super(timeSerde);
    }

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
