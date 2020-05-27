package stroom.data.retention.api;

import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class DataRetentionConfig extends AbstractConfig {

    @JsonProperty
    @JsonPropertyDescription("When the data retention deletion process runs this property controls the window of " +
            "data that will be logically deleted in each pass. To be of use it should be less than the " +
            "data retention job execution frequency. Its purpose is to reduce the quantity of data deleted in each pass " +
            "and thus reduce the length of time database locks are held for. If unset the data will be deleted across " +
            "as large a time range as possible."
    )
    private StroomDuration deleteBatchWindowSize = null;

    @JsonProperty
    @JsonPropertyDescription("If true stroom will add additional clauses to the data retention deletion SQL in order " +
            "to make use of other database indexes in order to improve performance. Due to the varied nature of " +
            "possible retention rules and data held on the system, this optimisation may be counter productive.")
    private boolean useQueryOptimisation = true;

    public StroomDuration getDeleteBatchWindowSize() {
        return deleteBatchWindowSize;
    }

    @SuppressWarnings("unused")
    public void setDeleteBatchWindowSize(final StroomDuration deleteBatchWindowSize) {
        this.deleteBatchWindowSize = deleteBatchWindowSize;
    }

    public boolean isUseQueryOptimisation() {
        return useQueryOptimisation;
    }

    @SuppressWarnings("unused")
    public void setUseQueryOptimisation(final boolean useQueryOptimisation) {
        this.useQueryOptimisation = useQueryOptimisation;
    }
}
