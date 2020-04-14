package stroom.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
public class MetaValueConfig extends AbstractConfig {
    private StroomDuration deleteAge = StroomDuration.ofDays(30);
    private int deleteBatchSize = 1000;
    private int flushBatchSize = 1000;
    private boolean addAsync = true;

    @JsonProperty
    @JsonPropertyDescription("The age of streams that we store meta data in the database for. " +
        "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    @SuppressWarnings("unused")
    public void setDeleteAge(final StroomDuration deleteAge) {
        this.deleteAge = deleteAge;
    }

    @JsonProperty
    @JsonPropertyDescription("How many stream attributes we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @SuppressWarnings("unused")
    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @JsonProperty
    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    @SuppressWarnings("unused")
    public void setFlushBatchSize(final int flushBatchSize) {
        this.flushBatchSize = flushBatchSize;
    }

    @JsonProperty
    public boolean isAddAsync() {
        return addAsync;
    }

    public void setAddAsync(final boolean addAsync) {
        this.addAsync = addAsync;
    }

    @Override
    public String toString() {
        return "MetaValueConfig{" +
                "deleteAge=" + deleteAge +
                ", deleteBatchSize=" + deleteBatchSize +
                ", flushBatchSize=" + flushBatchSize +
                ", addAsync=" + addAsync +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MetaValueConfig that = (MetaValueConfig) o;
        return deleteBatchSize == that.deleteBatchSize &&
                flushBatchSize == that.flushBatchSize &&
                addAsync == that.addAsync &&
                Objects.equals(deleteAge, that.deleteAge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deleteAge, deleteBatchSize, flushBatchSize, addAsync);
    }
}
