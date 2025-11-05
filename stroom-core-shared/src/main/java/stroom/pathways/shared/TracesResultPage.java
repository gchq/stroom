package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class TracesResultPage extends ResultPage<TraceRoot> {

    @JsonCreator
    public TracesResultPage(@JsonProperty("values") final List<TraceRoot> values,
                            @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
