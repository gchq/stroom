package stroom.job.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeListResponse extends ResultPage<JobNode> {

    public JobNodeListResponse(final List<JobNode> values) {
        super(values);
    }

    @JsonCreator
    public JobNodeListResponse(@JsonProperty("values") final List<JobNode> values,
                               @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    /**
     * Used for full queries (not bounded).
     */
    public static JobNodeListResponse createUnboundedJobeNodeResponse(final List<JobNode> realList) {
        if (realList != null) {
            return new JobNodeListResponse(realList, createPageResponse(realList));
        } else {
            return new JobNodeListResponse(Collections.emptyList());
        }
    }

}
