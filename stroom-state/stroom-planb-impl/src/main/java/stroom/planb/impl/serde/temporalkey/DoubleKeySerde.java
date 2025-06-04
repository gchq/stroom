package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

public class DoubleKeySerde extends SimpleTemporalKeySerde {

    public DoubleKeySerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Double.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final double d = byteBuffer.getDouble();
        return ValDouble.create(d);
    }

    @Override
    void writePrefix(final TemporalKey key, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDouble(key.getPrefix().getVal(), byteBuffer);
    }
}
