package stroom.data.retention.api;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class DataRetentionConfig extends AbstractConfig {

    private static final Boolean USE_QUERY_OPTIMISATION_DEFAULT = Boolean.TRUE;

    @JsonProperty
    @JsonPropertyDescription("The number of records that will be logically deleted in each pass of the data " +
            "retention deletion process. This number can be reduced to limit the time database locks are " +
            "held for.")
    private int deleteBatchSize = 1_000;

    @JsonProperty
    @JsonPropertyDescription("If true stroom will add additional clauses to the data retention deletion SQL in order " +
            "to make use of other database indexes in order to improve performance. Due to the varied nature of " +
            "possible retention rules and data held on the system, this optimisation may be counter productive.")
    private Boolean useQueryOptimisation = true;


    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @SuppressWarnings("unused")
    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    public boolean isUseQueryOptimisation() {
        return Objects.requireNonNullElse(useQueryOptimisation, USE_QUERY_OPTIMISATION_DEFAULT);
    }

    @SuppressWarnings("unused")
    public void setUseQueryOptimisation(final Boolean useQueryOptimisation) {
        this.useQueryOptimisation = Objects.requireNonNullElse(useQueryOptimisation, USE_QUERY_OPTIMISATION_DEFAULT);
    }
}
