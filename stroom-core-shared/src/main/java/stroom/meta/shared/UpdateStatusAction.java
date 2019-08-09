package stroom.meta.shared;

import stroom.task.shared.Action;
import stroom.util.shared.SharedInteger;

public class UpdateStatusAction extends Action<SharedInteger> {
    private FindMetaCriteria criteria;
    private Status newStatus;

    public UpdateStatusAction() {
    }

    public UpdateStatusAction(final FindMetaCriteria criteria, final Status newStatus) {
        this.criteria = criteria;
        this.newStatus = newStatus;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public Status getNewStatus() {
        return newStatus;
    }

    @Override
    public String getTaskName() {
        return "Update stream action";
    }
}
