package stroom.data.retention.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DataRetentionDeleteSummaryResponse extends ResultPage<DataRetentionDeleteSummary> {

    public DataRetentionDeleteSummaryResponse(final List<DataRetentionDeleteSummary> values) {
        super(values);
    }

    @JsonCreator
    public DataRetentionDeleteSummaryResponse(
            @JsonProperty("values") final List<DataRetentionDeleteSummary> values,
            @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
