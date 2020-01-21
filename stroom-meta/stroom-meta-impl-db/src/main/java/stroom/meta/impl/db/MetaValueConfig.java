package stroom.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class MetaValueConfig extends AbstractConfig {
    private StroomDuration deleteAge = StroomDuration.ofDays(30);
    private int deleteBatchSize = 1000;
    private int flushBatchSize = 1000;
    private boolean addAsync = true;

    @JsonPropertyDescription("The age of streams that we store meta data in the database for")
    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    @SuppressWarnings("unused")
    public void setDeleteAge(final StroomDuration deleteAge) {
        this.deleteAge = deleteAge;
    }

    @JsonPropertyDescription("How many stream attributes we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @SuppressWarnings("unused")
    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    @SuppressWarnings("unused")
    public void setFlushBatchSize(final int flushBatchSize) {
        this.flushBatchSize = flushBatchSize;
    }

    public boolean isAddAsync() {
        return addAsync;
    }

    public void setAddAsync(final boolean addAsync) {
        this.addAsync = addAsync;
    }

    @Override
    public String toString() {
        return "MetaValueConfig{" +
                "deleteAge='" + deleteAge + '\'' +
                ", deleteBatchSize=" + deleteBatchSize +
                ", flushBatchSize=" + flushBatchSize +
                ", addAsync=" + addAsync +
                '}';
    }
}
