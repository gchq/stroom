package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public final class DoubleSet extends AbstractSet<Double> implements ConstraintValue {

    @JsonCreator
    public DoubleSet(@JsonProperty("set") final Set<Double> set) {
        super(set);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.DOUBLE_SET;
    }
}
