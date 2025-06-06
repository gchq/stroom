package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

public class IntegerValTimeSerde extends SimpleValTimeSerde {

    public IntegerValTimeSerde(final TimeSerde timeSerde) {
        super(timeSerde);
    }

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
