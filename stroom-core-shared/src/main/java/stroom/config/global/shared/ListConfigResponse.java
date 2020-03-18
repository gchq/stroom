package stroom.config.global.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ListConfigResponse extends ResultPage<ConfigProperty> {

    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values) {
        super(values);
    }

    @JsonCreator
    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
