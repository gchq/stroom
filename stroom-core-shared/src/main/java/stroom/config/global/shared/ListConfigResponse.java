package stroom.config.global.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.QuickFilterResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@Schema(description = "List of config properties")
public class ListConfigResponse extends QuickFilterResultPage<ConfigProperty> {

    @JsonProperty
    private final String nodeName;

    public ListConfigResponse(final List<ConfigProperty> values,
                              final String nodeName,
                              final String qualifiedFilterInput) {
        super(values, qualifiedFilterInput);
        this.nodeName = nodeName;
    }

    @JsonCreator
    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse,
                              @JsonProperty("nodeName") final String nodeName,
                              @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse, qualifiedFilterInput);
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }
}
