package stroom.annotation.shared;

public class CreateEntryRequest {
    private long metaId;
    private long eventId;
    private String user;
    private String comment;
    private String status;
    private String assignedTo;

    public CreateEntryRequest(final long metaId,
                              final long eventId,
                              final String user,
                              final String comment,
                              final String status,
                              final String assignedTo) {
        this.metaId = metaId;
        this.eventId = eventId;
        this.user = user;
        this.comment = comment;
        this.status = status;
        this.assignedTo = assignedTo;
    }

    public long getMetaId() {
        return metaId;
    }

    public long getEventId() {
        return eventId;
    }

    private String getUser() {
        return user;
    }

    public String getComment() {
        return comment;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }
}
