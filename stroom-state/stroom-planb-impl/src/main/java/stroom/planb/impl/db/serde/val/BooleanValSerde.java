package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;

import java.nio.ByteBuffer;

public class BooleanValSerde extends SimpleValSerde {

    public BooleanValSerde(final ByteBuffers byteBuffers) {
        super(byteBuffers);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValBoolean.create(b != 0);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeBoolean(val, byteBuffer);
    }

    @Override
    int size() {
        return Byte.BYTES;
    }
}
