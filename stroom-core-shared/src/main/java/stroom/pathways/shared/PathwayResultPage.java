package stroom.pathways.shared;

import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class PathwayResultPage extends ResultPage<Pathway> {

    @JsonCreator
    public PathwayResultPage(@JsonProperty("values") final List<Pathway> values,
                             @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
