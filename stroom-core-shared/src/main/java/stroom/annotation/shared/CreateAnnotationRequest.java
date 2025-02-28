package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateAnnotationRequest {

    @JsonProperty
    private final String title;
    @JsonProperty
    private final String subject;
    @JsonProperty
    private final String status;
    @JsonProperty
    private final List<EventId> linkedEvents;

    @JsonCreator
    public CreateAnnotationRequest(@JsonProperty("title") final String title,
                                   @JsonProperty("subject") final String subject,
                                   @JsonProperty("status") final String status,
                                   @JsonProperty("linkedEvents") final List<EventId> linkedEvents) {
        this.title = title;
        this.subject = subject;
        this.status = status;
        this.linkedEvents = linkedEvents;
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateAnnotationRequest request = (CreateAnnotationRequest) o;
        return Objects.equals(title, request.title) &&
               Objects.equals(subject, request.subject) &&
               Objects.equals(status, request.status) &&
               Objects.equals(linkedEvents, request.linkedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, subject, status, linkedEvents);
    }

    @Override
    public String toString() {
        return "CreateAnnotationRequest{" +
               "title='" + title + '\'' +
               ", subject='" + subject + '\'' +
               ", status='" + status + '\'' +
               ", linkedEvents=" + linkedEvents +
               '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private String title;
        private String subject;
        private String status;
        private List<EventId> linkedEvents;

        public Builder() {
        }

        public Builder(final CreateAnnotationRequest doc) {
            this.title = doc.title;
            this.subject = doc.subject;
            this.status = doc.status;
            this.linkedEvents = doc.linkedEvents;
        }

        public Builder title(final String title) {
            this.title = title;
            return self();
        }

        public Builder subject(final String subject) {
            this.subject = subject;
            return self();
        }

        public Builder status(final String status) {
            this.status = status;
            return self();
        }

        public Builder linkedEvents(final List<EventId> linkedEvents) {
            this.linkedEvents = linkedEvents;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public CreateAnnotationRequest build() {
            return new CreateAnnotationRequest(
                    title, subject, status, linkedEvents);
        }
    }
}
