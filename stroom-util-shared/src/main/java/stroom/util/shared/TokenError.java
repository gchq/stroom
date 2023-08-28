package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class TokenError {

    @JsonProperty
    private final DefaultLocation from;
    @JsonProperty
    private final DefaultLocation to;
    @JsonProperty
    private final String text;

    @JsonCreator
    public TokenError(@JsonProperty("from") final DefaultLocation from,
                      @JsonProperty("to") final DefaultLocation to,
                      @JsonProperty("text") final String text) {
        this.from = from;
        this.to = to;
        this.text = text;
    }

    public DefaultLocation getFrom() {
        return from;
    }

    public DefaultLocation getTo() {
        return to;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TokenError that = (TokenError) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, text);
    }

    @Override
    public String toString() {
        return "TokenError{" +
                "from=" + from +
                ", to=" + to +
                ", text='" + text + '\'' +
                '}';
    }
}
