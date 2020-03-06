package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FetchNodeStatusResponse extends ResultPage<NodeStatusResult> {
    public FetchNodeStatusResponse(final List<NodeStatusResult> values) {
        super(values);
    }

    @JsonCreator
    public FetchNodeStatusResponse(@JsonProperty("values") final List<NodeStatusResult> values,
                                   @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
