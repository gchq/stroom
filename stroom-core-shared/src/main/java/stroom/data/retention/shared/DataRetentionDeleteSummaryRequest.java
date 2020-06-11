package stroom.data.retention.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DataRetentionDeleteSummaryRequest {

    @JsonProperty
    private final DataRetentionRules dataRetentionRules;
    @JsonProperty
    private final FindDataRetentionImpactCriteria criteria;

    @JsonCreator
    public DataRetentionDeleteSummaryRequest(@JsonProperty("dataRetentionRules") final DataRetentionRules dataRetentionRules,
                                             @JsonProperty("criteria") final FindDataRetentionImpactCriteria criteria) {
        this.dataRetentionRules = Objects.requireNonNull(dataRetentionRules);
        this.criteria = Objects.requireNonNull(criteria);
    }


    public DataRetentionRules getDataRetentionRules() {
        return dataRetentionRules;
    }

    public FindDataRetentionImpactCriteria getCriteria() {
        return criteria;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionDeleteSummaryRequest that = (DataRetentionDeleteSummaryRequest) o;
        return dataRetentionRules.equals(that.dataRetentionRules) &&
                criteria.equals(that.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataRetentionRules, criteria);
    }

    @Override
    public String toString() {
        return "DataRetentionDeleteSummaryRequest{" +
                "dataRetentionRules=" + dataRetentionRules +
                ", criteria=" + criteria +
                '}';
    }
}
