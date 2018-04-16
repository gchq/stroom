package stroom.security.shared;

import stroom.entity.shared.Action;
import stroom.query.api.v2.DocRef;

public class CopyPermissionsFromParentAction extends Action<DocumentPermissions> {
    private static final long serialVersionUID = -4110225584077027283L;

    private DocRef docRef;

    public CopyPermissionsFromParentAction(){
        // Default constructor necessary for GWT serialisation.
    }

    public CopyPermissionsFromParentAction(final DocRef docRef){
        this.docRef = docRef;
    }

    @Override
    public String getTaskName() {
        return "Copy Permissions From Parent";
    }

    public DocRef getDocRef() {
        return docRef;
    }
}
