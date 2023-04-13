package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class LifespanInfo {

    @JsonProperty
    private final String timeToIdle;
    @JsonProperty
    private final String timeToLive;
    @JsonProperty
    private final boolean destroyOnTabClose;
    @JsonProperty
    private final boolean destroyOnWindowClose;

    @JsonCreator
    public LifespanInfo(@JsonProperty("timeToIdle") final String timeToIdle,
                        @JsonProperty("timeToLive") final String timeToLive,
                        @JsonProperty("destroyOnTabClose") final boolean destroyOnTabClose,
                        @JsonProperty("destroyOnWindowClose") final boolean destroyOnWindowClose) {
        this.timeToIdle = timeToIdle;
        this.timeToLive = timeToLive;
        this.destroyOnTabClose = destroyOnTabClose;
        this.destroyOnWindowClose = destroyOnWindowClose;
    }

    public String getTimeToIdle() {
        return timeToIdle;
    }

    public String getTimeToLive() {
        return timeToLive;
    }

    public boolean isDestroyOnTabClose() {
        return destroyOnTabClose;
    }

    public boolean isDestroyOnWindowClose() {
        return destroyOnWindowClose;
    }
}
