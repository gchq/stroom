package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class DoubleRange extends AbstractRange<Double> implements ConstraintValue {

    @JsonCreator
    public DoubleRange(@JsonProperty("min") final Double min,
                       @JsonProperty("max") final Double max) {
        super(min, max);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.DOUBLE_RANGE;
    }
}
