package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpStructureElement extends QueryHelpData {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<String> snippets;

    @JsonCreator
    public QueryHelpStructureElement(@JsonProperty("description") final String description,
                                     @JsonProperty("snippets") final List<String> snippets) {
        this.description = description;
        this.snippets = snippets;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSnippets() {
        return snippets;
    }
}
