package stroom.annotation.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Annotation {

    public static final String TITLE = "Title";
    public static final String SUBJECT = "Subject";
    public static final String COMMENT = "Comment";
    public static final String STATUS = "Status";
    public static final String ASSIGNED_TO = "Assigned";
    public static final String LINK = "Link";
    public static final String UNLINK = "Unlink";

    @JsonProperty
    private Long id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTime;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTime;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String title;
    @JsonProperty
    private String subject;
    @JsonProperty
    private String status;
    @JsonProperty
    private UserRef assignedTo;
    @JsonProperty
    private String comment;
    @JsonProperty
    private String history;

    public Annotation() {
    }

    @JsonCreator
    public Annotation(@JsonProperty("id") final Long id,
                      @JsonProperty("version") final Integer version,
                      @JsonProperty("createTime") final Long createTime,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateTime") final Long updateTime,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("title") final String title,
                      @JsonProperty("subject") final String subject,
                      @JsonProperty("status") final String status,
                      @JsonProperty("assignedTo") final UserRef assignedTo,
                      @JsonProperty("comment") final String comment,
                      @JsonProperty("history") final String history) {
        this.id = id;
        this.version = version;
        this.createTime = createTime;
        this.createUser = createUser;
        this.updateTime = updateTime;
        this.updateUser = updateUser;
        this.title = title;
        this.subject = subject;
        this.status = status;
        this.assignedTo = assignedTo;
        this.comment = comment;
        this.history = history;
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

    public UserRef getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(final UserRef assignedTo) {
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
        return "id=" + id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Annotation that = (Annotation) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(version, that.version) &&
                Objects.equals(createTime, that.createTime) &&
                Objects.equals(createUser, that.createUser) &&
                Objects.equals(updateTime, that.updateTime) &&
                Objects.equals(updateUser, that.updateUser) &&
                Objects.equals(title, that.title) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(status, that.status) &&
                Objects.equals(assignedTo, that.assignedTo) &&
                Objects.equals(comment, that.comment) &&
                Objects.equals(history, that.history);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                version,
                createTime,
                createUser,
                updateTime,
                updateUser,
                title,
                subject,
                status,
                assignedTo,
                comment,
                history);
    }
}
