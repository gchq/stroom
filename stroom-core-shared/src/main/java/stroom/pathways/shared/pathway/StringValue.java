package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class StringValue extends AbstractValue<String> implements ConstraintValue {

    @JsonCreator
    public StringValue(@JsonProperty("value") final String value) {
        super(value);
    }

    public boolean validate(final String value) {
        return Objects.equals(this.getValue(), value);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.STRING;
    }
}
