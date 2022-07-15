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

    public EventStoreConfig() {
        rollFrequency = StroomDuration.ofMinutes(1);
    }

    @JsonCreator
    public EventStoreConfig(@JsonProperty("rollFrequency") final StroomDuration rollFrequency) {
        this.rollFrequency = rollFrequency;
    }

    public StroomDuration getRollFrequency() {
        return rollFrequency;
    }
}
