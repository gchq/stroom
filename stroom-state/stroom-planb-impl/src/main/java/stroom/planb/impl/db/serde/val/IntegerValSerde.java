package stroom.planb.impl.db.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class IntegerValSerde extends SimpleValSerde {

    public IntegerValSerde(final ByteBuffers byteBuffers) {
        super(byteBuffers);
    }

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeInteger(val, byteBuffer);
    }

    @Override
    int size() {
        return Integer.BYTES;
    }
}
