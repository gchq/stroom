package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ShortValSerde extends SimpleValSerde {

    public ShortValSerde(final ByteBuffers byteBuffers) {
        super(byteBuffers);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeShort(val, byteBuffer);
    }

    @Override
    int size() {
        return Short.BYTES;
    }
}
