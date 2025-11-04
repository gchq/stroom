package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class BooleanValue extends AbstractValue<Boolean> implements ConstraintValue {

    @JsonCreator
    public BooleanValue(@JsonProperty("value") final boolean value) {
        super(value);
    }

    public boolean validate(final Boolean value) {
        return Objects.equals(this.getValue(), value);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.BOOLEAN;
    }
}
