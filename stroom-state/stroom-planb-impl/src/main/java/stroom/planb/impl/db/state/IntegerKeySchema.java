package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.serde.ValSerde;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;
import java.util.function.Function;

class IntegerKeySchema extends SimpleKeySchema {

    IntegerKeySchema(final PlanBEnv env,
                     final ByteBuffers byteBuffers,
                     final Boolean overwrite,
                     final ValSerde valSerde) {
        super(env, byteBuffers, overwrite, valSerde);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        try {
            final int i = Integer.parseInt(key);
            return byteBuffers.useInt(i, function);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be an integer but could not parse '" +
                                       key +
                                       "' as integer");
        }
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValInteger.create(byteBuffer.getInt(0));
    }
}
