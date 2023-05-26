package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;

/**
 * Represents an entry with a null/empty/blank value
 */
public class NullValue implements RefDataValue {

    /**
     * MUST not change this else it is stored in the ref store. MUST be unique over all
     * {@link RefDataValue} impls.
     */
    public static final int TYPE_ID = 3;
    private static final ByteBuffer ZERO_LENGTH_BTYE_BUFFER = ByteBuffer.wrap(new byte[0]);
    private static final NullValue INSTANCE = new NullValue();

    private NullValue() {
    }

    public static NullValue getInstance() {
        return INSTANCE;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        return valueStoreHashAlgorithm.hash(ZERO_LENGTH_BTYE_BUFFER);
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean isNullValue() {
        return true;
    }
}
