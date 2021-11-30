package stroom.query.common.v2;

class LmdbKV {

    private final LmdbKey rowKey;
    private final LmdbValue rowValue;

    public LmdbKV(final LmdbKey rowKey,
                  final LmdbValue rowValue) {
        this.rowKey = rowKey;
        this.rowValue = rowValue;
    }

    public LmdbKey getRowKey() {
        return rowKey;
    }

    public LmdbValue getRowValue() {
        return rowValue;
    }

    @Override
    public String toString() {
        return "QueueItemImpl{" +
                "rowKey=" + rowKey +
                ", rowValue=" + rowValue +
                '}';
    }
}
