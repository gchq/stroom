package stroom.planb.impl.db.histogram;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class HistogramValue extends KV<HistogramKey, Long> implements PlanBValue {

    @JsonCreator
    public HistogramValue(@JsonProperty("key") final HistogramKey key,
                          @JsonProperty("value") final long value) {
        super(key, value);
    }
}
