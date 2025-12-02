package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class Regex extends AbstractValue<String> implements ConstraintValue {

    @JsonCreator
    public Regex(@JsonProperty("value") final String value) {
        super(value);
    }

    public ConstraintValueType valueType() {
        return ConstraintValueType.REGEX;
    }
}
