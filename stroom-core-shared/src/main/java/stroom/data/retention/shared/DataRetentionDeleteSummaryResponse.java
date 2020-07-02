package stroom.data.retention.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DataRetentionDeleteSummaryResponse extends ResultPage<DataRetentionDeleteSummary> {

    @JsonProperty
    private final String queryId;

    public DataRetentionDeleteSummaryResponse(final List<DataRetentionDeleteSummary> values,
                                              final String queryId) {
        super(values);
        this.queryId = queryId;
    }

    @JsonCreator
    public DataRetentionDeleteSummaryResponse(
            @JsonProperty("values") final List<DataRetentionDeleteSummary> values,
            @JsonProperty("pageResponse") final PageResponse pageResponse,
            @JsonProperty("queryId") final String queryId) {
        super(values, pageResponse);
        this.queryId = queryId;
    }

    public String getQueryId() {
        return queryId;
    }
}
