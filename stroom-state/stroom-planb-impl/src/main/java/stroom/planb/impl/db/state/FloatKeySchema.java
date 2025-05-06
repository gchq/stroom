package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import java.nio.ByteBuffer;

class FloatKeySchema extends SimpleKeySchema<Float> {

    FloatKeySchema(final PlanBEnv env,
                   final ByteBuffers byteBuffers,
                   final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Float parseKey(final String key) {
        try {
            return Float.parseFloat(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a float but could not parse '" +
                                       key +
                                       "' as float");
        }
    }

    @Override
    int keyLength(final Float key) {
        return Float.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Float key) {
        byteBuffer.putFloat(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValFloat.create(byteBuffer.getFloat(0));
    }
}
