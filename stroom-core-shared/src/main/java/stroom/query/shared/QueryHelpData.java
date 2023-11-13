package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryHelpDataSource.class, name = "dataSource"),
        @JsonSubTypes.Type(value = QueryHelpField.class, name = "field"),
        @JsonSubTypes.Type(value = QueryHelpFunctionSignature.class, name = "functionSignature"),
        @JsonSubTypes.Type(value = QueryHelpTitle.class, name = "title"),
        @JsonSubTypes.Type(value = QueryHelpStructureElement.class, name = "structureElement")
})
@JsonInclude(Include.NON_NULL)
public abstract class QueryHelpData {

}
