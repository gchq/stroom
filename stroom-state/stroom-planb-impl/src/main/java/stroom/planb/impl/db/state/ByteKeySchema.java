package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;
import java.util.function.Function;

class ByteKeySchema extends SimpleKeySchema {

    ByteKeySchema(final PlanBEnv env,
                  final ByteBuffers byteBuffers,
                  final Boolean overwrite,
                  final StateValueSerde stateValueSerde) {
        super(env, byteBuffers, overwrite, stateValueSerde);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        try {
            final byte b = Byte.parseByte(key);
            return byteBuffers.useByte(b, function);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                       key +
                                       "' as byte");
        }
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValInteger.create(byteBuffer.get(0));
    }
}
