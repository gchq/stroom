package stroom.core.snippet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Snippet {

    @JsonProperty
    private final String caption;
    @JsonProperty
    private final String snippet;

    @JsonCreator
    public Snippet(@JsonProperty("caption") String caption,
                   @JsonProperty("snippet") String snippet) {
        this.caption = caption;
        this.snippet = snippet;
    }

    public String getCaption() {
        return caption;
    }

    public String getSnippet() {
        return snippet;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Snippet snippet1 = (Snippet) o;
        return Objects.equals(caption, snippet1.caption) && Objects.equals(snippet, snippet1.snippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caption, snippet);
    }

    @Override
    public String toString() {
        return "Snippet{" +
                "caption='" + caption + '\'' +
                ", snippet='" + snippet + '\'' +
                '}';
    }
}
