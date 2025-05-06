package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import java.nio.ByteBuffer;
import java.util.function.Function;

class LongKeySchema extends SimpleKeySchema {

    LongKeySchema(final PlanBEnv env,
                  final ByteBuffers byteBuffers,
                  final Boolean overwrite) {
        super(env, byteBuffers, overwrite);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        try {
            final long l = Long.parseLong(key);
            return byteBuffers.useLong(l, function);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Expected state key to be an long but could not parse '" +
                                       key +
                                       "' as long");
        }
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValLong.create(byteBuffer.getLong(0));
    }
}
