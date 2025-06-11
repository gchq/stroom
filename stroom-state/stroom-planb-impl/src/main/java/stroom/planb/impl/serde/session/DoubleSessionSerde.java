package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class DoubleSessionSerde extends SimpleSessionSerde {

    public DoubleSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Double.BYTES);
    }

    @Override
    Val readPrefix(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final double d = byteBuffer.getDouble();
        return ValDouble.create(d);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDouble(val, byteBuffer);
    }
}
