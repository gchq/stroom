package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public final class AnyTypeValue implements ConstraintValue {

    @Override
    public String toString() {
        return "?";
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.ANY;
    }
}
