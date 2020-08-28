package stroom.meta.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import java.util.Objects;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class MetaValueConfig extends AbstractConfig {

    @JsonProperty
    @JsonPropertyDescription("The age of streams that we store meta data in the database for. " +
            "In ISO-8601 duration format, e.g. 'P1DT12H'")
    private StroomDuration deleteAge = StroomDuration.ofDays(30);

    @JsonProperty
    @JsonPropertyDescription("How many stream attributes we want to try and delete in a single batch.")
    private int deleteBatchSize = 500;

    @JsonProperty
    @JsonPropertyDescription("The number of stream attributes to queue before flushing to the database. " +
            "Only applicable if property 'addAsync' is true.")
    private int flushBatchSize = 500;

    @JsonProperty
    @JsonPropertyDescription("If true, stream attributes will be queued in memory until the queue " +
            "reaches 'flushBatchSize'. If false, stream attributes will be written to the database " +
            "immediately and synchronously.")
    private boolean addAsync = true;


    public StroomDuration getDeleteAge() {
        return deleteAge;
    }

    @SuppressWarnings("unused")
    public void setDeleteAge(final StroomDuration deleteAge) {
        this.deleteAge = deleteAge;
    }

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

    @SuppressWarnings("unused")
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
