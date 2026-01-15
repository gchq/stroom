/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.annotation.shared;

import stroom.util.shared.UserRef;

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
    private final UserRef assignTo;
    @JsonProperty
    private final String comment;
    @JsonProperty
    private final AnnotationTable table;
    @JsonProperty
    private final List<EventId> linkedEvents;
    @JsonProperty
    private final List<Long> linkedAnnotations;

    @JsonCreator
    public CreateAnnotationRequest(@JsonProperty("title") final String title,
                                   @JsonProperty("subject") final String subject,
                                   @JsonProperty("status") final String status,
                                   @JsonProperty("assignTo") final UserRef assignTo,
                                   @JsonProperty("comment") final String comment,
                                   @JsonProperty("table") final AnnotationTable table,
                                   @JsonProperty("linkedEvents") final List<EventId> linkedEvents,
                                   @JsonProperty("linkedAnnotations") final List<Long> linkedAnnotations) {
        this.title = title;
        this.subject = subject;
        this.status = status;
        this.assignTo = assignTo;
        this.comment = comment;
        this.table = table;
        this.linkedEvents = linkedEvents;
        this.linkedAnnotations = linkedAnnotations;
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

    public UserRef getAssignTo() {
        return assignTo;
    }

    public String getComment() {
        return comment;
    }

    public AnnotationTable getTable() {
        return table;
    }

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }

    public List<Long> getLinkedAnnotations() {
        return linkedAnnotations;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateAnnotationRequest that = (CreateAnnotationRequest) o;
        return Objects.equals(title, that.title) &&
               Objects.equals(subject, that.subject) &&
               Objects.equals(status, that.status) &&
               Objects.equals(assignTo, that.assignTo) &&
               Objects.equals(comment, that.comment) &&
               Objects.equals(table, that.table) &&
               Objects.equals(linkedEvents, that.linkedEvents) &&
               Objects.equals(linkedAnnotations, that.linkedAnnotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, subject, status, assignTo, comment, table, linkedEvents, linkedAnnotations);
    }

    @Override
    public String toString() {
        return "CreateAnnotationRequest{" +
               "title='" + title + '\'' +
               ", subject='" + subject + '\'' +
               ", status='" + status + '\'' +
               ", assignTo=" + assignTo +
               ", comment='" + comment + '\'' +
               ", table=" + table +
               ", linkedEvents=" + linkedEvents +
               ", linkedAnnotations=" + linkedAnnotations +
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
        private UserRef assignTo;
        private String comment;
        private AnnotationTable table;
        private List<EventId> linkedEvents;
        private List<Long> linkedAnnotations;

        public Builder() {
        }

        public Builder(final CreateAnnotationRequest request) {
            this.title = request.title;
            this.subject = request.subject;
            this.status = request.status;
            this.assignTo = request.assignTo;
            this.comment = request.comment;
            this.table = request.table;
            this.linkedEvents = request.linkedEvents;
            this.linkedAnnotations = request.linkedAnnotations;
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

        public Builder assignTo(final UserRef assignTo) {
            this.assignTo = assignTo;
            return self();
        }

        public Builder comment(final String comment) {
            this.comment = comment;
            return self();
        }

        public Builder table(final AnnotationTable table) {
            this.table = table;
            return self();
        }

        public Builder linkedEvents(final List<EventId> linkedEvents) {
            this.linkedEvents = linkedEvents;
            return self();
        }

        public Builder linkedAnnotations(final List<Long> linkedAnnotations) {
            this.linkedAnnotations = linkedAnnotations;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public CreateAnnotationRequest build() {
            return new CreateAnnotationRequest(
                    title,
                    subject,
                    status,
                    assignTo,
                    comment,
                    table,
                    linkedEvents,
                    linkedAnnotations);
        }
    }
}
