package stroom.job.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeAndInfoListResponse extends ResultPage<JobNodeAndInfo> {

    public JobNodeAndInfoListResponse(final List<JobNodeAndInfo> values) {
        super(values);
    }

    @JsonCreator
    public JobNodeAndInfoListResponse(@JsonProperty("values") final List<JobNodeAndInfo> values,
                                      @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    /**
     * Used for full queries (not bounded).
     */
    public static JobNodeAndInfoListResponse createUnboundedResponse(final List<JobNodeAndInfo> realList) {
        if (realList != null) {
            return new JobNodeAndInfoListResponse(realList, createPageResponse(realList));
        } else {
            return new JobNodeAndInfoListResponse(Collections.emptyList());
        }
    }
}
