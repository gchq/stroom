package stroom.annotation.shared;

import java.util.List;

public class SetAssignedToRequest {
    private List<Long> annotationIdList;
    private String assignedTo;

    public SetAssignedToRequest() {
    }

    public SetAssignedToRequest(final List<Long> annotationIdList,
                                final String assignedTo) {
        this.annotationIdList = annotationIdList;
        this.assignedTo = assignedTo;
    }

    public List<Long> getAnnotationIdList() {
        return annotationIdList;
    }

    public void setAnnotationIdList(final List<Long> annotationIdList) {
        this.annotationIdList = annotationIdList;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(final String assignedTo) {
        this.assignedTo = assignedTo;
    }
}
