package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

class IntegerKeySchema extends SimpleKeySchema<Integer> {

    IntegerKeySchema(final PlanBEnv env,
                     final ByteBuffers byteBuffers,
                     final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Integer parseKey(final String key) {
        try {
            return Integer.parseInt(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be an integer but could not parse '" +
                                       key +
                                       "' as integer");
        }
    }

    @Override
    int keyLength(final Integer key) {
        return Integer.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Integer key) {
        byteBuffer.putInt(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValInteger.create(byteBuffer.getInt(0));
    }
}
