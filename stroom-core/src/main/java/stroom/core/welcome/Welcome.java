package stroom.core.welcome;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"html"})
@JsonInclude(Include.NON_NULL)
public class Welcome {
    @JsonProperty
    private final String html;

    @JsonCreator
    public Welcome(@JsonProperty("html") final String html) {
        this.html = html;
    }

    public String getHtml() {
        return html;
    }
}
