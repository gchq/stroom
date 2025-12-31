package stroom.ai.shared;

import stroom.dashboard.shared.DashboardSearchRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class DashboardTableData extends AskStroomAiData {

    @JsonProperty
    private final DashboardSearchRequest searchRequest;

    @JsonCreator
    public DashboardTableData(@JsonProperty("chatMemoryId") final String chatMemoryId,
                              @JsonProperty("searchRequest") final DashboardSearchRequest searchRequest) {
        super(chatMemoryId);
        this.searchRequest = searchRequest;
    }

    public DashboardSearchRequest getSearchRequest() {
        return searchRequest;
    }
}
