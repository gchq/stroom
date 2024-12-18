package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class DocumentUserPermissionsRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final UserRef userRef;

    @JsonCreator
    public DocumentUserPermissionsRequest(@JsonProperty("docRef") final DocRef docRef,
                                          @JsonProperty("userRef") final UserRef userRef) {
        this.docRef = docRef;
        this.userRef = userRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }
}
