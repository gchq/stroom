package stroom.annotation.shared;

import stroom.annotation.shared.AnnotationEntry.EntryType;

public class CreateEntryRequest {
    private long metaId;
    private long eventId;
    private EntryType entryType;
    private String title;
    private String subject;
    private String comment;
    private String status;
    private String assignedTo;

    public CreateEntryRequest() {
    }

    public CreateEntryRequest(final long metaId,
                              final long eventId,
                              final EntryType entryType,
                              final String title,
                              final String subject,
                              final String comment,
                              final String status,
                              final String assignedTo) {
        this.metaId = metaId;
        this.eventId = eventId;
        this.entryType = entryType;
        this.title = title;
        this.subject = subject;
        this.comment = comment;
        this.status = status;
        this.assignedTo = assignedTo;
    }

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

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(final EntryType entryType) {
        this.entryType = entryType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(final String subject) {
        this.subject = subject;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
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
