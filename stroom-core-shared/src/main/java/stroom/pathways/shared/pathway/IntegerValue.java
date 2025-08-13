package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class IntegerValue extends AbstractValue<Integer> implements Constraint {

    @JsonCreator
    public IntegerValue(@JsonProperty("value") final int value) {
        super(value);
    }
}
