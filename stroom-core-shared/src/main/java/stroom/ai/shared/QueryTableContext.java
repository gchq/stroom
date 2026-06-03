package stroom.ai.shared;

import stroom.query.shared.QuerySearchRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class QueryTableContext extends AskStroomAiContext {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String node;
    @JsonProperty
    private final QuerySearchRequest searchRequest;

    @JsonCreator
    public QueryTableContext(@JsonProperty("description") final String description,
                             @JsonProperty("node") final String node,
                             @JsonProperty("searchRequest") final QuerySearchRequest searchRequest) {
        this.description = description;
        this.node = node;
        this.searchRequest = searchRequest;
    }

    @Override
    public String getDescription() {
        return description != null
                ? description
                : "Query table";
    }

    public QuerySearchRequest getSearchRequest() {
        return searchRequest;
    }

    public String getNode() {
        return node;
    }
}
