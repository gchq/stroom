package stroom.annotation.shared;

import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class Annotation extends Doc {

    public static final String TYPE = "Annotation";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANNOTATION_DOCUMENT_TYPE;

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
    private String subject;
    @JsonProperty
    private String status;
    @JsonProperty
    private UserRef assignedTo;
    @JsonProperty
    private String comment;
    @JsonProperty
    private String history;
    @JsonProperty
    private String description;

    public Annotation() {
    }

    @JsonCreator
    public Annotation(@JsonProperty("type") final String type,
                      @JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("id") final Long id,
                      @JsonProperty("subject") final String subject,
                      @JsonProperty("status") final String status,
                      @JsonProperty("assignedTo") final UserRef assignedTo,
                      @JsonProperty("comment") final String comment,
                      @JsonProperty("history") final String history,
                      @JsonProperty("description") final String description) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.id = id;
        this.subject = subject;
        this.status = status;
        this.assignedTo = assignedTo;
        this.comment = comment;
        this.history = history;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }
}
