package stroom.security.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class SingleDocumentPermissionChangeRequest implements PermissionChangeRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final AbstractDocumentPermissionsChange change;

    @JsonCreator
    public SingleDocumentPermissionChangeRequest(@JsonProperty("docRef") final DocRef docRef,
                                                 @JsonProperty("change") final AbstractDocumentPermissionsChange
                                                         change) {
        Objects.requireNonNull(docRef, "docRef is null");
        Objects.requireNonNull(change, "Request is null");
        this.docRef = docRef;
        this.change = change;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public AbstractDocumentPermissionsChange getChange() {
        return change;
    }
}
