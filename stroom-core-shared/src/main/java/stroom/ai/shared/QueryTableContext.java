package stroom.ai.shared;

import stroom.query.shared.QuerySearchRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class QueryTableContext extends AskStroomAiContext {

    @JsonProperty
    private final String node;
    @JsonProperty
    private final QuerySearchRequest searchRequest;

    @JsonCreator
    public QueryTableContext(@JsonProperty("chatMemoryId") final String chatMemoryId,
                             @JsonProperty("node") final String node,
                             @JsonProperty("searchRequest") final QuerySearchRequest searchRequest) {
        super(chatMemoryId);
        this.node = node;
        this.searchRequest = searchRequest;
    }

    public QuerySearchRequest getSearchRequest() {
        return searchRequest;
    }

    public String getNode() {
        return node;
    }
}
