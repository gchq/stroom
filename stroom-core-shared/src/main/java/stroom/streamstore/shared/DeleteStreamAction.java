package stroom.streamstore.shared;

import stroom.entity.shared.Action;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.util.shared.SharedInteger;

public class DeleteStreamAction extends Action<SharedInteger> {
    private FindStreamCriteria criteria;

    public DeleteStreamAction() {
    }

    public DeleteStreamAction(final FindStreamCriteria criteria) {
        this.criteria = criteria;
    }

    public FindStreamCriteria getCriteria() {
        return criteria;
    }

    @Override
    public String getTaskName() {
        return "Delete stream action";
    }
}
