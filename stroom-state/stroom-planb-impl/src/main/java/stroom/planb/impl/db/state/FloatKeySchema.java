package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import java.nio.ByteBuffer;
import java.util.function.Function;

class FloatKeySchema extends SimpleKeySchema {

    FloatKeySchema(final PlanBEnv env,
                   final ByteBuffers byteBuffers,
                   final Boolean overwrite,
                   final ValSerde stateValueSerde) {
        super(env, byteBuffers, overwrite, stateValueSerde);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        try {
            final float f = Float.parseFloat(key);
            return byteBuffers.useFloat(f, function);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a float but could not parse '" +
                                       key +
                                       "' as float");
        }
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValFloat.create(byteBuffer.getFloat(0));
    }
}
