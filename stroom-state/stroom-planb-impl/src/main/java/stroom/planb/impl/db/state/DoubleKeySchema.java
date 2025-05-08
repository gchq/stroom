package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;
import java.util.function.Function;

class DoubleKeySchema extends SimpleKeySchema {

    DoubleKeySchema(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite,
                    final ValSerde stateValueSerde) {
        super(env, byteBuffers, overwrite, stateValueSerde);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        try {
            final double d = Double.parseDouble(key);
            return byteBuffers.useDouble(d, function);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a double but could not parse '" +
                                       key +
                                       "' as double");
        }
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValDouble.create(byteBuffer.getDouble(0));
    }
}
