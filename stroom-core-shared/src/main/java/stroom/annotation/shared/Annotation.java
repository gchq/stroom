package stroom.annotation.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Annotation extends AbstractDoc {

    public static final String TYPE = "Annotation";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANNOTATION_DOCUMENT_TYPE;

    @JsonProperty
    private final Long id;
    @JsonProperty
    private final String subject;
    @JsonProperty
    private final AnnotationTag status;
    @JsonProperty
    private final UserRef assignedTo;
    @JsonProperty
    private final List<AnnotationTag> labels;
    @JsonProperty
    private final List<AnnotationTag> collections;
    @JsonProperty
    private final String comment;
    @JsonProperty
    private final String history;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final SimpleDuration retentionPeriod;
    @JsonProperty
    private final Long retainUntilTimeMs;

    @JsonCreator
    public Annotation(@JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("id") final Long id,
                      @JsonProperty("subject") final String subject,
                      @JsonProperty("status") final AnnotationTag status,
                      @JsonProperty("assignedTo") final UserRef assignedTo,
                      @JsonProperty("labels") final List<AnnotationTag> labels,
                      @JsonProperty("collections") final List<AnnotationTag> collections,
                      @JsonProperty("comment") final String comment,
                      @JsonProperty("history") final String history,
                      @JsonProperty("description") final String description,
                      @JsonProperty("retentionPeriod") final SimpleDuration retentionPeriod,
                      @JsonProperty("retainUntilTimeMs") final Long retainUntilTimeMs) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.id = id;
        this.subject = subject;
        this.status = status;
        this.assignedTo = assignedTo;
        this.labels = labels;
        this.collections = collections;
        this.comment = comment;
        this.history = history;
        this.description = description;
        this.retentionPeriod = retentionPeriod;
        this.retainUntilTimeMs = retainUntilTimeMs;
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public AnnotationTag getStatus() {
        return status;
    }

    public UserRef getAssignedTo() {
        return assignedTo;
    }

    public List<AnnotationTag> getLabels() {
        return labels;
    }

    public List<AnnotationTag> getCollections() {
        return collections;
    }

    public String getComment() {
        return comment;
    }

    public String getHistory() {
        return history;
    }

    public String getDescription() {
        return description;
    }

    public SimpleDuration getRetentionPeriod() {
        return retentionPeriod;
    }

    public Long getRetainUntilTimeMs() {
        return retainUntilTimeMs;
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Annotation that = (Annotation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Annotation, Annotation.Builder> {

        private Long id;
        private String subject;
        private AnnotationTag status;
        private UserRef assignedTo;
        private List<AnnotationTag> labels;
        private List<AnnotationTag> collections;
        private String comment;
        private String history;
        private String description;
        private SimpleDuration retentionPeriod;
        private Long retainUntilTimeMs;

        public Builder() {
        }

        public Builder(final Annotation doc) {
            super(doc);
            this.id = doc.id;
            this.subject = doc.subject;
            this.status = doc.status;
            this.assignedTo = doc.assignedTo;
            this.labels = doc.labels;
            this.collections = doc.collections;
            this.comment = doc.comment;
            this.history = doc.history;
            this.description = doc.description;
            this.retentionPeriod = doc.retentionPeriod;
            this.retainUntilTimeMs = doc.retainUntilTimeMs;
        }

        public Builder id(final Long id) {
            this.id = id;
            return self();
        }

        public Builder subject(final String subject) {
            this.subject = subject;
            return self();
        }

        public Builder status(final AnnotationTag status) {
            this.status = status;
            return self();
        }

        public Builder assignedTo(final UserRef assignedTo) {
            this.assignedTo = assignedTo;
            return self();
        }

        public Builder labels(final List<AnnotationTag> labels) {
            this.labels = labels;
            return self();
        }

        public Builder collections(final List<AnnotationTag> collections) {
            this.collections = collections;
            return self();
        }

        public Builder comment(final String comment) {
            this.comment = comment;
            return self();
        }

        public Builder history(final String history) {
            this.history = history;
            return self();
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder retentionPeriod(final SimpleDuration retentionPeriod) {
            this.retentionPeriod = retentionPeriod;
            return self();
        }

        public Builder retainUntilTimeMs(final Long retainUntilTimeMs) {
            this.retainUntilTimeMs = retainUntilTimeMs;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Annotation build() {
            return new Annotation(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    id,
                    subject,
                    status,
                    assignedTo,
                    labels,
                    collections,
                    comment,
                    history,
                    description,
                    retentionPeriod,
                    retainUntilTimeMs);
        }
    }
}
