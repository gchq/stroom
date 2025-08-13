package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class StringValue extends AbstractValue<String> implements Constraint {

    @JsonCreator
    public StringValue(@JsonProperty("value") final String value) {
        super(value);
    }
}
