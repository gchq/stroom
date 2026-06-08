package stroom.ai.shared;

import stroom.dashboard.shared.DashboardSearchRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class DashboardTableContext extends AskStroomAiContext {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String node;
    @JsonProperty
    private final DashboardSearchRequest searchRequest;

    @JsonCreator
    public DashboardTableContext(@JsonProperty("description") final String description,
                                 @JsonProperty("node") final String node,
                                 @JsonProperty("searchRequest") final DashboardSearchRequest searchRequest) {
        this.description = description;
        this.node = node;
        this.searchRequest = searchRequest;
    }

    @Override
    public String getDescription() {
        return description != null
                ? description
                : "Dashboard table";
    }

    public DashboardSearchRequest getSearchRequest() {
        return searchRequest;
    }

    public String getNode() {
        return node;
    }
}
