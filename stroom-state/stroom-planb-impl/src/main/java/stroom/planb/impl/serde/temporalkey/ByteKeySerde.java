package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import java.nio.ByteBuffer;

public class ByteKeySerde extends SimpleTemporalKeySerde {

    public ByteKeySerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Byte.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    void writePrefix(final TemporalKey key, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeByte(key.getPrefix().getVal(), byteBuffer);
    }
}
