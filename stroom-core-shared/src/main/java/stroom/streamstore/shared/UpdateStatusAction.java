package stroom.streamstore.shared;

import stroom.data.meta.api.DataStatus;
import stroom.task.shared.Action;
import stroom.data.meta.api.FindDataCriteria;
import stroom.util.shared.SharedInteger;

public class UpdateStatusAction extends Action<SharedInteger> {
    private FindDataCriteria criteria;
    private DataStatus newStatus;

    public UpdateStatusAction() {
    }

    public UpdateStatusAction(final FindDataCriteria criteria, final DataStatus newStatus) {
        this.criteria = criteria;
        this.newStatus = newStatus;
    }

    public FindDataCriteria getCriteria() {
        return criteria;
    }

    public DataStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String getTaskName() {
        return "Update stream action";
    }
}
