package stroom.job.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeInfoListResponse extends ResultPage<JobNodeInfo> {

    public JobNodeInfoListResponse(final List<JobNodeInfo> values) {
        super(values);
    }

    @JsonCreator
    public JobNodeInfoListResponse(@JsonProperty("values") final List<JobNodeInfo> values,
                                   @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    /**
     * Used for full queries (not bounded).
     */
    public static JobNodeInfoListResponse createUnboundedJobNodeResponse(final List<JobNodeInfo> realList) {
        if (realList != null) {
            return new JobNodeInfoListResponse(realList, createPageResponse(realList));
        } else {
            return new JobNodeInfoListResponse(Collections.emptyList());
        }
    }

}
