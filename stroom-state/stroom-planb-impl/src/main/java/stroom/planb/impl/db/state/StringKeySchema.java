package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

class StringKeySchema extends SimpleKeySchema {

    StringKeySchema(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite,
                    final ValSerde stateValueSerde) {
        super(env, byteBuffers, overwrite, stateValueSerde);
    }

    @Override
    <R> R useKey(final String key, final Function<ByteBuffer, R> function) {
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 511) {
            throw new RuntimeException("Key length exceeds 511 bytes");
        }
        return byteBuffers.useBytes(bytes, function);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValString.create(new String(ByteBufferUtils.toBytes(byteBuffer), StandardCharsets.UTF_8));
    }
}
