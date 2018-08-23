package stroom.explorer.shared;

import stroom.task.shared.Action;
import stroom.entity.shared.SharedDocRefInfo;
import stroom.docref.DocRef;

public class ExplorerServiceInfoAction extends Action<SharedDocRefInfo> {

    private DocRef docRef;

    /**
     * Default constructor for GWT serialisation
     */
    public ExplorerServiceInfoAction() {

    }

    public ExplorerServiceInfoAction(final DocRef docRef) {
        this.docRef = docRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    @Override
    public String getTaskName() {
        return null;
    }
}
