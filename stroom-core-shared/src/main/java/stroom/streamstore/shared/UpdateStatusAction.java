package stroom.streamstore.shared;

import stroom.data.meta.api.StreamStatus;
import stroom.entity.shared.Action;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.util.shared.SharedInteger;

public class UpdateStatusAction extends Action<SharedInteger> {
    private FindStreamCriteria criteria;
    private StreamStatus newStatus;

    public UpdateStatusAction() {
    }

    public UpdateStatusAction(final FindStreamCriteria criteria, final StreamStatus newStatus) {
        this.criteria = criteria;
        this.newStatus = newStatus;
    }

    public FindStreamCriteria getCriteria() {
        return criteria;
    }

    public StreamStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String getTaskName() {
        return "Update stream action";
    }
}
