package stroom.pipeline.refdata.store;

/**
 * Represents an entry with a null/empty/blank value
 */
public class NullValue implements RefDataValue {

    private static final NullValue INSTANCE = new NullValue();

    private NullValue() {
    }

    public static NullValue getInstance() {
        return INSTANCE;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        throw new UnsupportedOperationException("getValueHashCode not supported for "
                + this.getClass().getSimpleName());
    }

    @Override
    public int getTypeId() {
        throw new UnsupportedOperationException("getTypeId not supported for "
                + this.getClass().getSimpleName());
    }
}
