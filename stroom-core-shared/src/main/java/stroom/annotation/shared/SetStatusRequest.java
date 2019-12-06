package stroom.annotation.shared;

import java.util.List;

public class SetStatusRequest {
    private List<Long> annotationIdList;
    private String status;

    public SetStatusRequest() {
    }

    public SetStatusRequest(final List<Long> annotationIdList,
                            final String status) {
        this.annotationIdList = annotationIdList;
        this.status = status;
    }

    public List<Long> getAnnotationIdList() {
        return annotationIdList;
    }

    public void setAnnotationIdList(final List<Long> annotationIdList) {
        this.annotationIdList = annotationIdList;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }
}
