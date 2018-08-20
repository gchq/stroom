package stroom.data.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

@Singleton
public class MetaValueConfig {
    private String deleteAge = "30d";
    private int deleteBatchSize = 1000;
    private int flushBatchSize = 1000;
    private boolean addAsync = true;

    @JsonPropertyDescription("The age of streams that we store meta data in the database for")
    public String getDeleteAge() {
        return deleteAge;
    }

    public void setDeleteAge(final String deleteAge) {
        this.deleteAge = deleteAge;
    }

    @JsonIgnore
    long getDeleteAgeMs() {
        return ModelStringUtil.parseDurationString(deleteAge);
    }

    @JsonPropertyDescription("How many stream attributes we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public void setFlushBatchSize(final int flushBatchSize) {
        this.flushBatchSize = flushBatchSize;
    }

    public boolean isAddAsync() {
        return addAsync;
    }

    public void setAddAsync(final boolean addAsync) {
        this.addAsync = addAsync;
    }
}
