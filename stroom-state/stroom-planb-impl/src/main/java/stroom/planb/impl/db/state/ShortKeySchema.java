package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

class ShortKeySchema extends SimpleKeySchema<Short> {

    ShortKeySchema(final PlanBEnv env,
                   final ByteBuffers byteBuffers,
                   final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Short parseKey(final String key) {
        try {
            return Short.parseShort(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a short but could not parse '" +
                                       key +
                                       "' as short");
        }
    }

    @Override
    int keyLength(final Short key) {
        return Short.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Short key) {
        byteBuffer.putShort(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValInteger.create(byteBuffer.getShort(0));
    }
}
