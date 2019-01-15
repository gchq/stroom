package stroom.processor;

public interface BatchDeleteConfig {
    String getDeletePurgeAge();

    int getDeleteBatchSize();
}
