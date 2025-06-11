package stroom.planb.impl.data;

import stroom.lmdb2.KV;
import stroom.planb.impl.serde.temporalkey.TemporalKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public final class TemporalValue extends KV<TemporalKey, Long> implements PlanBValue {

    @JsonCreator
    public TemporalValue(@JsonProperty("key") final TemporalKey key,
                         @JsonProperty("value") final long value) {
        super(key, value);
    }
}
