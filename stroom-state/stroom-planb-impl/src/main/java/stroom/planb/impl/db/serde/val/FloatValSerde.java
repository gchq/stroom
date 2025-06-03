package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class FloatValSerde extends SimpleValSerde {

    public FloatValSerde(final ByteBuffers byteBuffers) {
        super(byteBuffers);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeFloat(val, byteBuffer);
    }

    @Override
    int size() {
        return Float.BYTES;
    }
}
