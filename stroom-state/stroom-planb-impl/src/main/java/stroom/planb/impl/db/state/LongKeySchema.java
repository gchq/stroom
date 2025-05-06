package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import java.nio.ByteBuffer;

class LongKeySchema extends SimpleKeySchema<Long> {

    LongKeySchema(final PlanBEnv env,
                  final ByteBuffers byteBuffers,
                  final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Long parseKey(final String key) {
        try {
            return Long.parseLong(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be an long but could not parse '" +
                                       key +
                                       "' as long");
        }
    }

    @Override
    int keyLength(final Long key) {
        return Long.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Long key) {
        byteBuffer.putLong(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValLong.create(byteBuffer.getLong(0));
    }
}
