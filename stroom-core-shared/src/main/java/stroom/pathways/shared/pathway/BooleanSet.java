package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public final class BooleanSet extends AbstractSet<Boolean> implements Constraint {

    @JsonCreator
    public BooleanSet(@JsonProperty("set") final Set<Boolean> set) {
        super(set);
    }

    public boolean validate(final Boolean value) {
        return getSet().contains(value);
    }
}
