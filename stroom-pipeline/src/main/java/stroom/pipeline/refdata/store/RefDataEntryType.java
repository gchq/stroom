package stroom.pipeline.refdata.store;

/**
 * Defines the type of entries within a map within a reference data stream.
 */
public enum RefDataEntryType {
    /**
     * All entries are key-value entries.
     */
    KEY_VALUE,
    /**
     * All entries are range-value entries.
     */
    RANGE_VALUE,
    /**
     * Entries are a mixture of key-value and range-value entries.
     */
    MIXED,
    ;

    public boolean hasKeyValueEntries() {
        return this == KEY_VALUE || this == MIXED;
    }

    public boolean hasRangeValueEntries() {
        return this == RANGE_VALUE || this == MIXED;
    }
}
