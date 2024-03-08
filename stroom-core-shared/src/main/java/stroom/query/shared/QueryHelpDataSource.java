package stroom.query.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpDataSource extends QueryHelpData {

    @JsonProperty
    @JsonPropertyDescription("The document reference of the data source.")
    private final DocRef docRef;


    @JsonCreator
    public QueryHelpDataSource(@JsonProperty("docRef") final DocRef docRef) {
        this.docRef = docRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }
}
