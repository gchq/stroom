package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Lifespan {

    @JsonProperty
    private final long timeToIdleMs;
    @JsonProperty
    private final long timeToLiveMs;
    @JsonProperty
    private final boolean destroyOnTabClose;
    @JsonProperty
    private final boolean destroyOnWindowClose;

    @JsonCreator
    public Lifespan(@JsonProperty("timeToIdleMs") final long timeToIdleMs,
                    @JsonProperty("timeToLiveMs") final long timeToLiveMs,
                    @JsonProperty("destroyOnTabClose") final boolean destroyOnTabClose,
                    @JsonProperty("destroyOnWindowClose") final boolean destroyOnWindowClose) {
        this.timeToIdleMs = timeToIdleMs;
        this.timeToLiveMs = timeToLiveMs;
        this.destroyOnTabClose = destroyOnTabClose;
        this.destroyOnWindowClose = destroyOnWindowClose;
    }

    public long getTimeToIdleMs() {
        return timeToIdleMs;
    }

    public long getTimeToLiveMs() {
        return timeToLiveMs;
    }

    public boolean isDestroyOnTabClose() {
        return destroyOnTabClose;
    }

    public boolean isDestroyOnWindowClose() {
        return destroyOnWindowClose;
    }
}
