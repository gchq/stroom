package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "actualExecutionTimeMs",
        "lastEffectiveExecutionTimeMs",
        "nextEffectiveExecutionTimeMs"
})
public class ExecutionTracker {

    @JsonProperty
    private final long actualExecutionTimeMs;
    @JsonProperty
    private final long lastEffectiveExecutionTimeMs;
    @JsonProperty
    private final long nextEffectiveExecutionTimeMs;

    @JsonCreator
    public ExecutionTracker(@JsonProperty("actualExecutionTimeMs") final long actualExecutionTimeMs,
                            @JsonProperty("lastEffectiveExecutionTimeMs") final long lastEffectiveExecutionTimeMs,
                            @JsonProperty("nextEffectiveExecutionTimeMs") final long nextEffectiveExecutionTimeMs) {
        this.actualExecutionTimeMs = actualExecutionTimeMs;
        this.lastEffectiveExecutionTimeMs = lastEffectiveExecutionTimeMs;
        this.nextEffectiveExecutionTimeMs = nextEffectiveExecutionTimeMs;
    }

    public long getActualExecutionTimeMs() {
        return actualExecutionTimeMs;
    }

    public long getLastEffectiveExecutionTimeMs() {
        return lastEffectiveExecutionTimeMs;
    }

    public long getNextEffectiveExecutionTimeMs() {
        return nextEffectiveExecutionTimeMs;
    }
}
