package stroom.query.api;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ResultStoreInfo {

    @JsonProperty
    private final SearchRequestSource searchRequestSource;
    @JsonProperty
    private final QueryKey queryKey;
    @JsonProperty
    private final UserRef owner;
    @JsonProperty
    private final long creationTime;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final long storeSize;
    @JsonProperty
    private final boolean complete;
    @JsonProperty
    private final SearchTaskProgress taskProgress;
    @JsonProperty
    private final LifespanInfo searchProcessLifespan;
    @JsonProperty
    private final LifespanInfo storeLifespan;

    @JsonCreator
    public ResultStoreInfo(
            @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
            @JsonProperty("queryKey") final QueryKey queryKey,
            @JsonProperty("owner") final UserRef owner,
            @JsonProperty("creationTime") final long creationTime,
            @JsonProperty("nodeName") final String nodeName,
            @JsonProperty("storeSize") final long storeSize,
            @JsonProperty("complete") final boolean complete,
            @JsonProperty("taskProgress") final SearchTaskProgress taskProgress,
            @JsonProperty("searchProcessLifespan") final LifespanInfo searchProcessLifespan,
            @JsonProperty("storeLifespan") final LifespanInfo storeLifespan) {
        this.searchRequestSource = searchRequestSource;
        this.queryKey = queryKey;
        this.owner = owner;
        this.creationTime = creationTime;
        this.nodeName = nodeName;
        this.storeSize = storeSize;
        this.complete = complete;
        this.taskProgress = taskProgress;
        this.searchProcessLifespan = searchProcessLifespan;
        this.storeLifespan = storeLifespan;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public UserRef getOwner() {
        return owner;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getNodeName() {
        return nodeName;
    }

    public long getStoreSize() {
        return storeSize;
    }

    public boolean isComplete() {
        return complete;
    }

    public SearchTaskProgress getTaskProgress() {
        return taskProgress;
    }

    public LifespanInfo getSearchProcessLifespan() {
        return searchProcessLifespan;
    }

    public LifespanInfo getStoreLifespan() {
        return storeLifespan;
    }
}
