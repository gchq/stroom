package stroom.importexport.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

import java.util.Map;

@JsonInclude(Include.NON_DEFAULT)
public class Base64EncodedDocumentData {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final Map<String, String> dataMap;

    @JsonCreator
    public Base64EncodedDocumentData(@JsonProperty("docRef") final DocRef docRef,
                                     @JsonProperty("dataMap") final Map<String, String> dataMap) {
        this.docRef = docRef;
        this.dataMap = dataMap;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public Map<String, String> getDataMap() {
        return dataMap;
    }
}
