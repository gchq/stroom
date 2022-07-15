package stroom.proxy.app.event;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class EventStoreConfig extends AbstractConfig implements IsProxyConfig {

    @JsonProperty
    private final StroomDuration rollFrequency;
    @JsonProperty
    private final StroomDuration maxAge;
    @JsonProperty
    private final long maxEventCount;
    @JsonProperty
    private final long maxByteCount;

    public EventStoreConfig() {
        rollFrequency = StroomDuration.ofSeconds(10);
        maxAge = StroomDuration.ofMinutes(1);
        maxEventCount = Long.MAX_VALUE;
        maxByteCount = Long.MAX_VALUE;
    }

    @JsonCreator
    public EventStoreConfig(@JsonProperty("rollFrequency") final StroomDuration rollFrequency,
                            @JsonProperty("maxAge") final StroomDuration maxAge,
                            @JsonProperty("maxEventCount") final long maxEventCount,
                            @JsonProperty("maxByteCount") final long maxByteCount) {
        this.rollFrequency = rollFrequency;
        this.maxAge = maxAge;
        this.maxEventCount = maxEventCount;
        this.maxByteCount = maxByteCount;
    }

    public StroomDuration getRollFrequency() {
        return rollFrequency;
    }

    public StroomDuration getMaxAge() {
        return maxAge;
    }

    public long getMaxEventCount() {
        return maxEventCount;
    }

    public long getMaxByteCount() {
        return maxByteCount;
    }
}
