package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpTitle extends QueryHelpData {

    @JsonProperty
    private final String documentation;

    @JsonCreator
    public QueryHelpTitle(@JsonProperty("documentation") final String documentation) {
        this.documentation = documentation;
    }

    public String getDocumentation() {
        return documentation;
    }
}
