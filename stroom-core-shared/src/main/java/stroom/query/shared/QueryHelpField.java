package stroom.query.shared;

import stroom.datasource.api.v2.AbstractField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpField extends QueryHelpData {

    @JsonProperty
    @JsonPropertyDescription("A field of a data source.")
    private AbstractField field;

    @JsonCreator
    public QueryHelpField(@JsonProperty("field") final AbstractField field) {
        this.field = field;
    }

    public AbstractField getField() {
        return field;
    }

    public void setField(final AbstractField field) {
        this.field = field;
    }
}
