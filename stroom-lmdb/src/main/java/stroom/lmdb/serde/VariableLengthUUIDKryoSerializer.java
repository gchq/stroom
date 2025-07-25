package stroom.lmdb.serde;

import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.UUID;

/**
 * Kryo serializer for a string representation of a {@link UUID}. The string form must
 * conform to the output generated by {@link UUID#toString()}.
 * <p>
 * May use variable length serialisation so the length of the serialised form is unknown.
 */
public class VariableLengthUUIDKryoSerializer implements KryoSerializer<String> {

    // Two variable length longs at 1-9 bytes each
    public static final int BUFFER_CAPACITY = 9 * 2;

    @Override
    public void write(final Output output, final String uuidStr) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("uuidStr [{}] is not a valid UUID", uuidStr), e);
        }
        output.writeLong(uuid.getMostSignificantBits(), false);
        output.writeLong(uuid.getLeastSignificantBits(), false);
    }

    @Override
    public String read(final Input input) {
        final long uuidHighBits = input.readLong(false);
        final long uuidLowBits = input.readLong(false);
        return new UUID(uuidHighBits, uuidLowBits).toString();
    }
}
