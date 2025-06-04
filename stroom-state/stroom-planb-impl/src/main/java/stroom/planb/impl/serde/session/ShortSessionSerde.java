package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class ShortSessionSerde extends SimpleSessionSerde {

    public ShortSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Short.BYTES);
    }

    @Override
    Val readPrefix(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeShort(val, byteBuffer);
    }
}
