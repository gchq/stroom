package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class IntegerRange extends AbstractRange<Integer> implements ConstraintValue {

    @JsonCreator
    public IntegerRange(@JsonProperty("min") final Integer min,
                        @JsonProperty("max") final Integer max) {
        super(min, max);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.INTEGER_RANGE;
    }
}
