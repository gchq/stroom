package stroom.data.retention.impl;

import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class DataRetentionConfig implements IsConfig {
    private int deleteBatchSize = 1000;
    private String uuid;

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
