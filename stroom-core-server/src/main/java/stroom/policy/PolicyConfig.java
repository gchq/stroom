package stroom.policy;

import javax.inject.Singleton;

@Singleton
public class PolicyConfig {
    private int deleteBatchSize = 1000;

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }
}
