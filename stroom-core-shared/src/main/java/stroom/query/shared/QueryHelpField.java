package stroom.query.shared;

import stroom.datasource.api.v2.FieldInfo;

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
    private final FieldInfo fieldInfo;

    @JsonCreator
    public QueryHelpField(@JsonProperty("fieldInfo") final FieldInfo fieldInfo) {
        this.fieldInfo = fieldInfo;
    }

    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }
}
