package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class FloatSessionSerde extends SimpleSessionSerde {

    public FloatSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Float.BYTES);
    }

    @Override
    Val readPrefix(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeFloat(val, byteBuffer);
    }
}
