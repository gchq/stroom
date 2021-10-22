package stroom.config.global.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@Schema(description = "List of config properties")
public class ListConfigResponse extends ResultPage<ConfigProperty> {

    @JsonProperty
    private final String nodeName;

    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values,
                              @JsonProperty("nodeName") final String nodeName) {
        super(values);
        this.nodeName = nodeName;
    }

    @JsonCreator
    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse,
                              @JsonProperty("nodeName") final String nodeName) {
        super(values, pageResponse);
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }
}
