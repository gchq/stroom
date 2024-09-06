package stroom.util.shared.string;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This is to get round our client rest code not liking plain text output.
 * Think we need to change it to use TextCallback
 * rather than MethodCallback for text content.
 */
@JsonInclude(Include.NON_NULL)
public class StringWrapper {

    @JsonProperty
    private final String string;

    @JsonCreator
    public StringWrapper(@JsonProperty("string") final String string) {
        this.string = string;
    }

    public static StringWrapper wrap(final String string) {
        return new StringWrapper(string);
    }

    public String getString() {
        return string;
    }

    @Override
    public String toString() {
        return string;
    }
}
