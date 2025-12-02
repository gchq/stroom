package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public final class AnyBoolean implements ConstraintValue {

    public boolean validate(final Boolean value) {
        return true;
    }

    @Override
    public ConstraintValueType valueType() {
        return ConstraintValueType.ANY_BOOLEAN;
    }
}
