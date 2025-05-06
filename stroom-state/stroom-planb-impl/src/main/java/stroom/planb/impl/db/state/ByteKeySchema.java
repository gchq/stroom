package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

class ByteKeySchema extends SimpleKeySchema<Byte> {

    ByteKeySchema(final PlanBEnv env,
                  final ByteBuffers byteBuffers,
                  final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    Byte parseKey(final String key) {
        try {
            return Byte.parseByte(key);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                       key +
                                       "' as byte");
        }
    }

    @Override
    int keyLength(final Byte key) {
        return Byte.BYTES;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final Byte key) {
        byteBuffer.put(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValInteger.create(byteBuffer.get(0));
    }
}
