package stroom.security.shared;

import stroom.docref.DocRef;

public class CopyPermissionsFromParentRequest {
    private DocRef docRef;

    public CopyPermissionsFromParentRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public CopyPermissionsFromParentRequest(final DocRef docRef) {
        this.docRef = docRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }
}