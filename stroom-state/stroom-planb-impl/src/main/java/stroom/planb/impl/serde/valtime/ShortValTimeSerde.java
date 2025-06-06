package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import java.nio.ByteBuffer;

public class ShortValTimeSerde extends SimpleValTimeSerde {

    public ShortValTimeSerde(final TimeSerde timeSerde) {
        super(timeSerde);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeShort(val, byteBuffer);
    }

    @Override
    int size() {
        return Short.BYTES;
    }
}
