package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class StringKeySchema extends SimpleKeySchema<byte[]> {

    StringKeySchema(final PlanBEnv envSupport,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite) {
        super(envSupport, byteBuffers, overwrite);
    }

    @Override
    byte[] parseKey(final String key) {
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 511) {
            throw new RuntimeException("Key length exceeds 511 bytes");
        }
        return bytes;
    }

    @Override
    int keyLength(final byte[] key) {
        return key.length;
    }

    @Override
    void writeKey(final ByteBuffer byteBuffer, final byte[] key) {
        byteBuffer.put(0, key);
    }

    @Override
    Val createKeyVal(final ByteBuffer byteBuffer) {
        return ValString.create(new String(ByteBufferUtils.toBytes(byteBuffer), StandardCharsets.UTF_8));
    }
}
