package stroom.query.common.v2;

class CurrentDbStateLmdbQueueItem implements LmdbQueueItem {

    private final CurrentDbState currentDbState;

    public CurrentDbStateLmdbQueueItem(final CurrentDbState currentDbState) {
        this.currentDbState = currentDbState;
    }

    public CurrentDbState getCurrentDbState() {
        return currentDbState;
    }

    @Override
    public String toString() {
        return "CurrentDbStateLmdbQueueItem{" +
                "currentDbState=" + currentDbState +
                '}';
    }
}
