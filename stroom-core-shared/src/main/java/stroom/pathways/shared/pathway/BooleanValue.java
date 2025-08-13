package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class BooleanValue extends AbstractValue<Boolean> implements Constraint {

    @JsonCreator
    public BooleanValue(@JsonProperty("value") final boolean value) {
        super(value);
    }
}
