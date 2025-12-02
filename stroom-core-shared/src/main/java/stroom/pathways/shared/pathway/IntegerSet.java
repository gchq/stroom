package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public final class IntegerSet extends AbstractSet<Integer> implements ConstraintValue {

    @JsonCreator
    public IntegerSet(@JsonProperty("set") final Set<Integer> set) {
        super(set);
    }

    public boolean validate(final Integer value) {
        return getSet().contains(value);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.INTEGER_SET;
    }
}
