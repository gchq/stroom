package stroom.query.common.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ResultStoreSettings {

    @JsonProperty
    private final Lifespan searchProcessLifespan;
    @JsonProperty
    private final Lifespan storeLifespan;

    @JsonCreator
    public ResultStoreSettings(@JsonProperty("searchProcessLifespan") final Lifespan searchProcessLifespan,
                               @JsonProperty("storeLifespan") final Lifespan storeLifespan) {
        this.searchProcessLifespan = searchProcessLifespan;
        this.storeLifespan = storeLifespan;
    }

    public Lifespan getSearchProcessLifespan() {
        return searchProcessLifespan;
    }

    public Lifespan getStoreLifespan() {
        return storeLifespan;
    }
}
