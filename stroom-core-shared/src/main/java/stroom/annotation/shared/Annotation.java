package stroom.annotation.shared;

public class Annotation {
    public static final String TITLE = "Title";
    public static final String SUBJECT = "Subject";
    public static final String COMMENT = "Comment";
    public static final String STATUS = "Status";
    public static final String ASSIGNED_TO = "Assigned";

    private Long id;
    private Integer version;
    private Long createTime;
    private String createUser;
    private Long updateTime;
    private String updateUser;
    private Long streamId;
    private Long eventId;
    private String title;
    private String subject;
    private String status;
    private String assignedTo;
    private String comment;
    private String history;

    public Annotation() {
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final Long createTime) {
        this.createTime = createTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(final Long streamId) {
        this.streamId = streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(final Long eventId) {
        this.eventId = eventId;
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

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public String getHistory() {
        return history;
    }

    public void setHistory(final String history) {
        this.history = history;
    }

    @Override
    public String toString() {
        if (id != null) {
            return String.valueOf(id);
        }
        if (streamId != null && eventId != null) {
            return streamId + ":" + eventId;
        }

        return null;
    }
}
