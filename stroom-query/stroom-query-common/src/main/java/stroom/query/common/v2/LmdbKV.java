package stroom.query.common.v2;

class LmdbKV implements LmdbQueueItem {

    private final CurrentDbState currentDbState;
    private final LmdbRowKey rowKey;
    private final LmdbValue rowValue;

    public LmdbKV(final CurrentDbState currentDbState,
                  final LmdbRowKey rowKey,
                  final LmdbValue rowValue) {
        this.currentDbState = currentDbState;
        this.rowKey = rowKey;
        this.rowValue = rowValue;
    }

    public CurrentDbState getCurrentDbState() {
        return currentDbState;
    }

    public LmdbRowKey getRowKey() {
        return rowKey;
    }

    public LmdbValue getRowValue() {
        return rowValue;
    }

    @Override
    public String toString() {
        return "LmdbKV{" +
                "currentDbState=" + currentDbState +
                ", rowKey=" + rowKey +
                ", rowValue=" + rowValue +
                '}';
    }
}
