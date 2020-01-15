package stroom.data.retention.impl;

import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class DataRetentionConfig extends AbstractConfig {
    private int deleteBatchSize = 1000;

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @Override
    public String toString() {
        return "DataRetentionConfig{" +
                "deleteBatchSize=" + deleteBatchSize +
                '}';
    }
}
