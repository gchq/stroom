package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import java.nio.ByteBuffer;

public class ByteValSerde extends SimpleValSerde {

    public ByteValSerde(final ByteBuffers byteBuffers) {
        super(byteBuffers);
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
