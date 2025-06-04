package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;

import java.nio.ByteBuffer;

public class DateValTimeSerde extends SimpleValTimeSerde {

    public DateValTimeSerde(final TimeSerde timeSerde) {
        super(timeSerde);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValDate.create(l);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeDate(val, byteBuffer);
    }

    @Override
    int size() {
        return Long.BYTES;
    }
}
