package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class KeyLength {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyLength.class);

    private KeyLength() {
        // Util
    }

    public static void check(final ByteBuffer byteBuffer, final int max) {
        try {
            check(byteBuffer.remaining(), max);
        } catch (final KeyLengthException e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.trace(() -> e.getMessage() +
                               "\n\n" +
                               ByteBufferUtils.byteBufferToHexAll(byteBuffer.duplicate()), e);
            throw e;
        }
    }

    public static void check(final int length, final int max) {
        if (length > max) {
            throw new KeyLengthException(max);
        }
    }
}
