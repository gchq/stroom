package stroom.processor.impl;

public interface BatchDeleteConfig {
    String getDeletePurgeAge();

    int getDeleteBatchSize();
}
