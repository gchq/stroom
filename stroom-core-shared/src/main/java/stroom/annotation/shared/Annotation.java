package stroom.annotation.shared;

public class Annotation {
    private long metaId;
    private long eventId;
    private String createdBy;
    private long createdOn;
    private String status;
    private String assignedTo;

    public long getMetaId() {
        return metaId;
    }

    public void setMetaId(final long metaId) {
        this.metaId = metaId;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(final long eventId) {
        this.eventId = eventId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final long createdOn) {
        this.createdOn = createdOn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(final String assignedTo) {
        this.assignedTo = assignedTo;
    }
}
