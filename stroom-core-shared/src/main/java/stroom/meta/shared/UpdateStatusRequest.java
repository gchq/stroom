package stroom.meta.shared;

public class UpdateStatusRequest {
    private FindMetaCriteria criteria;
    private Status newStatus;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(final FindMetaCriteria criteria, final Status newStatus) {
        this.criteria = criteria;
        this.newStatus = newStatus;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public Status getNewStatus() {
        return newStatus;
    }
}
