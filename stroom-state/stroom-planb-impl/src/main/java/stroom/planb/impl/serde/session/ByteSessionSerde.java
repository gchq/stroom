package stroom.planb.impl.serde.session;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class ByteSessionSerde extends SimpleSessionSerde {

    public ByteSessionSerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Byte.BYTES);
    }

    @Override
    Val readPrefix(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeByte(val, byteBuffer);
    }
}
