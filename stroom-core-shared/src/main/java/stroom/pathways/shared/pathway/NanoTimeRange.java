package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class NanoTimeRange extends AbstractRange<NanoTime> implements ConstraintValue {

    @JsonCreator
    public NanoTimeRange(@JsonProperty("min") final NanoTime min,
                         @JsonProperty("max") final NanoTime max) {
        super(min, max);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.DURATION_RANGE;
    }
}
