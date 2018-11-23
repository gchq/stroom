package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.task.shared.Action;

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