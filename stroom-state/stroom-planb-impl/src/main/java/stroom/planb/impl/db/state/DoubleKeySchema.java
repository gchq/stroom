package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

class DoubleKeySchema extends SimpleKeySchema<Double> {

    DoubleKeySchema(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Double parseKey(final String key) {
        try {
            return Double.parseDouble(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a double but could not parse '" +
                                       key +
                                       "' as double");
        }
    }

    @Override
    int keyLength(final Double key) {
        return Double.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Double key) {
        byteBuffer.putDouble(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValDouble.create(byteBuffer.getDouble(0));
    }
}
