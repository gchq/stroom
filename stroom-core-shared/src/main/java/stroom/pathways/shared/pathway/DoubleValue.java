package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class DoubleValue extends AbstractValue<Double> implements Constraint {

    @JsonCreator
    public DoubleValue(@JsonProperty("value") final double value) {
        super(value);
    }
}
