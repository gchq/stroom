package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class DateSessionSerde extends SimpleSessionSerde {

    public DateSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Long.BYTES);
    }

    @Override
    Val readPrefix(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValDate.create(l);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDate(val, byteBuffer);
    }
}
