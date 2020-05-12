package stroom.data.retention.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class DataRetentionConfig extends AbstractConfig {

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The number of streams to delete in a single batch when applying data retention rules.")
    private int deleteBatchSize = 1000;

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @SuppressWarnings("unused")
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
