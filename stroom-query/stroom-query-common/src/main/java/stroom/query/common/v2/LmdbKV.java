package stroom.query.common.v2;

class LmdbKV {

    private final LmdbRowKey rowKey;
    private final LmdbValue rowValue;

    public LmdbKV(final LmdbRowKey rowKey,
                  final LmdbValue rowValue) {
        this.rowKey = rowKey;
        this.rowValue = rowValue;
    }

    public LmdbRowKey getRowKey() {
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
