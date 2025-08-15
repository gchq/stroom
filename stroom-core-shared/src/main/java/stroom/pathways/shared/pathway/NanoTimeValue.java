package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class NanoTimeValue extends AbstractValue<NanoTime> implements ConstraintValue {

    @JsonCreator
    public NanoTimeValue(@JsonProperty("value") final NanoTime value) {
        super(value);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.DURATION_VALUE;
    }
}
