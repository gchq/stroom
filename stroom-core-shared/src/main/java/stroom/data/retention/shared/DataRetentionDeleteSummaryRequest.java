package stroom.data.retention.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DataRetentionDeleteSummaryRequest {

    @JsonProperty
    private final String queryId;
    @JsonProperty
    private final DataRetentionRules dataRetentionRules;
    @JsonProperty
    private final FindDataRetentionImpactCriteria criteria;

    @JsonCreator
    public DataRetentionDeleteSummaryRequest(@JsonProperty("queryId") final String queryId,
                                             @JsonProperty("dataRetentionRules") final DataRetentionRules dataRetentionRules,
                                             @JsonProperty("criteria") final FindDataRetentionImpactCriteria criteria) {
        this.queryId = queryId;
        this.dataRetentionRules = dataRetentionRules;
        this.criteria = criteria;
    }

    public String getQueryId() {
        return queryId;
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
        final DataRetentionDeleteSummaryRequest request = (DataRetentionDeleteSummaryRequest) o;
        return Objects.equals(queryId, request.queryId) &&
                Objects.equals(dataRetentionRules, request.dataRetentionRules) &&
                Objects.equals(criteria, request.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, dataRetentionRules, criteria);
    }

    @Override
    public String toString() {
        return "DataRetentionDeleteSummaryRequest{" +
                "queryId='" + queryId + '\'' +
                ", dataRetentionRules=" + dataRetentionRules +
                ", criteria=" + criteria +
                '}';
    }
}
