package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ProcessorListRowResultPage extends ResultPage<ProcessorListRow> {
    @JsonCreator
    public ProcessorListRowResultPage(@JsonProperty("values") final List<ProcessorListRow> values,
                                      @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
