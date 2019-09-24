package stroom.annotation.shared;

import stroom.annotation.shared.AnnotationEntry.EntryType;

public class CreateEntryRequest {
    private long metaId;
    private long eventId;
    private EntryType entryType;
    private String comment;
    private String status;
    private String assignedTo;

    public CreateEntryRequest(final long metaId,
                              final long eventId,
                              final EntryType entryType,
                              final String comment,
                              final String status,
                              final String assignedTo) {
        this.metaId = metaId;
        this.eventId = eventId;
        this.entryType = entryType;
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

    public EntryType getEntryType() {
        return entryType;
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
