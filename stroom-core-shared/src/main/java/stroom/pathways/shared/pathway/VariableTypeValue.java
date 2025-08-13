package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public final class VariableTypeValue implements Constraint {

    @Override
    public String toString() {
        return "?";
    }
}
