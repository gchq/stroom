package stroom.policy;

import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class PolicyConfig implements IsConfig {
    private int deleteBatchSize = 1000;

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @Override
    public String toString() {
        return "PolicyConfig{" +
                "deleteBatchSize=" + deleteBatchSize +
                '}';
    }
}
