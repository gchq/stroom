package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import java.nio.ByteBuffer;

public class ByteValTimeSerde extends SimpleValTimeSerde {

    public ByteValTimeSerde(final TimeSerde timeSerde) {
        super(timeSerde);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeByte(val, byteBuffer);
    }

    @Override
    int size() {
        return Byte.BYTES;
    }
}
