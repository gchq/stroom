package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class QueryHelpDetail {

    @JsonProperty
    private final InsertType insertType;
    @JsonProperty

    private final String insertText;
    @JsonProperty

    private final String documentation;

    @JsonCreator
    public QueryHelpDetail(@JsonProperty("insertType") final InsertType insertType,
                           @JsonProperty("insertText") final String insertText,
                           @JsonProperty("documentation") final String documentation) {
        this.insertType = insertType;
        this.insertText = insertText;
        this.documentation = documentation;
    }

    public InsertType getInsertType() {
        return insertType;
    }

    public String getInsertText() {
        return insertText;
    }

    public String getDocumentation() {
        return documentation;
    }
}
