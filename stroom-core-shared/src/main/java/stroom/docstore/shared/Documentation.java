package stroom.docstore.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@SuppressWarnings("ClassCanBeRecord") // cos GWT
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Documentation {

    public static final Documentation EMPTY = new Documentation(null);

    @JsonProperty
    private final String markdown;

    @JsonCreator
    public Documentation(@JsonProperty("markdown") final String markdown) {
        this.markdown = markdown;
    }

    public static Documentation of(final String markdown) {
        if (NullSafe.isBlankString(markdown)) {
            return EMPTY;
        } else {
            return new Documentation(markdown);
        }
    }

    public String getMarkdown() {
        return markdown;
    }
}
