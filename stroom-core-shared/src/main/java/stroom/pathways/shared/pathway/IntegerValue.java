package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class IntegerValue extends AbstractValue<Integer> implements ConstraintValue {

    @JsonCreator
    public IntegerValue(@JsonProperty("value") final int value) {
        super(value);
    }

    public boolean validate(final Integer value) {
        return Objects.equals(this.getValue(), value);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.INTEGER;
    }
}
