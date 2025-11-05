package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public final class StringSet extends AbstractSet<String> implements ConstraintValue {

    @JsonCreator
    public StringSet(@JsonProperty("set") final Set<String> set) {
        super(set);
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.STRING_SET;
    }
}
