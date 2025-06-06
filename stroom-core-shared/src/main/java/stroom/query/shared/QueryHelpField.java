package stroom.query.shared;

import stroom.query.api.datasource.QueryField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class QueryHelpField extends QueryHelpData {

    @JsonProperty
    @JsonPropertyDescription("A field of a data source.")
    private final QueryField field;

    @JsonCreator
    public QueryHelpField(@JsonProperty("field") final QueryField field) {
        this.field = field;
    }

    public QueryField getField() {
        return field;
    }
}
