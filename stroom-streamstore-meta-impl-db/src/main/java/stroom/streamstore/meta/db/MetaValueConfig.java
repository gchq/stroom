package stroom.streamstore.meta.db;

import javax.inject.Singleton;

@Singleton
class MetaValueConfig {
    private final long deleteAge;
    private final int deleteBatchSize;
    private final int flushBatchSize;
    private boolean addAsync = true;

    MetaValueConfig(final long deleteAge,
                    final int deleteBatchSize,
                    final int flushBatchSize,
                    final boolean addAsync) {
        this.deleteAge = deleteAge;
        this.deleteBatchSize = deleteBatchSize;
        this.flushBatchSize = flushBatchSize;
        this.addAsync = addAsync;
    }

    long getDeleteAge() {
        return deleteAge;
    }

    int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    int getFlushBatchSize() {
        return flushBatchSize;
    }

    boolean isAddAsync() {
        return addAsync;
    }

    void setAddAsync(final boolean addAsync) {
        this.addAsync = addAsync;
    }
}
