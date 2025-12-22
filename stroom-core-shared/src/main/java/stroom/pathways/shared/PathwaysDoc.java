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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "pathways"})
@JsonInclude(Include.NON_NULL)
public class PathwaysDoc extends AbstractDoc {

    public static final String TYPE = "Pathways";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PATHWAYS_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private SimpleDuration temporalOrderingTolerance;
    @JsonProperty
    private List<Pathway> pathways;
    @JsonProperty
    private boolean allowPathwayCreation = true;
    @JsonProperty
    private boolean allowPathwayMutation = true;
    @JsonProperty
    private boolean allowConstraintCreation = true;
    @JsonProperty
    private boolean allowConstraintMutation = true;
    @JsonProperty
    private DocRef tracesDocRef;
    @JsonProperty
    private DocRef infoFeed;
    @JsonProperty
    private String processingNode;

    @JsonCreator
    public PathwaysDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance,
                       @JsonProperty("pathways") final List<Pathway> pathways,
                       @JsonProperty("allowPathwayCreation") final boolean allowPathwayCreation,
                       @JsonProperty("allowPathwayMutation") final boolean allowPathwayMutation,
                       @JsonProperty("allowConstraintCreation") final boolean allowConstraintCreation,
                       @JsonProperty("allowConstraintMutation") final boolean allowConstraintMutation,
                       @JsonProperty("tracesDocRef") final DocRef tracesDocRef,
                       @JsonProperty("infoFeed") final DocRef infoFeed,
                       @JsonProperty("processingNode") final String processingNode) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
        this.pathways = pathways;
        this.allowPathwayCreation = allowPathwayCreation;
        this.allowPathwayMutation = allowPathwayMutation;
        this.allowConstraintCreation = allowConstraintCreation;
        this.allowConstraintMutation = allowConstraintMutation;
        this.tracesDocRef = tracesDocRef;
        this.infoFeed = infoFeed;
        this.processingNode = processingNode;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    public void setTemporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
        this.temporalOrderingTolerance = temporalOrderingTolerance;
    }

    public List<Pathway> getPathways() {
        return pathways;
    }

    public void setPathways(final List<Pathway> pathways) {
        this.pathways = pathways;
    }

    public boolean isAllowPathwayCreation() {
        return allowPathwayCreation;
    }

    public void setAllowPathwayCreation(final boolean allowPathwayCreation) {
        this.allowPathwayCreation = allowPathwayCreation;
    }

    public boolean isAllowPathwayMutation() {
        return allowPathwayMutation;
    }

    public void setAllowPathwayMutation(final boolean allowPathwayMutation) {
        this.allowPathwayMutation = allowPathwayMutation;
    }

    public boolean isAllowConstraintCreation() {
        return allowConstraintCreation;
    }

    public void setAllowConstraintCreation(final boolean allowConstraintCreation) {
        this.allowConstraintCreation = allowConstraintCreation;
    }

    public boolean isAllowConstraintMutation() {
        return allowConstraintMutation;
    }

    public void setAllowConstraintMutation(final boolean allowConstraintMutation) {
        this.allowConstraintMutation = allowConstraintMutation;
    }

    public DocRef getTracesDocRef() {
        return tracesDocRef;
    }

    public void setTracesDocRef(final DocRef tracesDocRef) {
        this.tracesDocRef = tracesDocRef;
    }

    public DocRef getInfoFeed() {
        return infoFeed;
    }

    public void setInfoFeed(final DocRef infoFeed) {
        this.infoFeed = infoFeed;
    }

    public String getProcessingNode() {
        return processingNode;
    }

    public void setProcessingNode(final String processingNode) {
        this.processingNode = processingNode;
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
        final PathwaysDoc that = (PathwaysDoc) o;
        return allowPathwayCreation == that.allowPathwayCreation &&
               allowPathwayMutation == that.allowPathwayMutation &&
               allowConstraintCreation == that.allowConstraintCreation &&
               allowConstraintMutation == that.allowConstraintMutation &&
               Objects.equals(description, that.description) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance) &&
               Objects.equals(pathways, that.pathways) &&
               Objects.equals(tracesDocRef, that.tracesDocRef) &&
               Objects.equals(infoFeed, that.infoFeed) &&
               Objects.equals(processingNode, that.processingNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                temporalOrderingTolerance,
                pathways,
                allowPathwayCreation,
                allowPathwayMutation,
                allowConstraintCreation,
                allowConstraintMutation,
                tracesDocRef,
                infoFeed,
                processingNode);
    }

    @Override
    public String toString() {
        return "PathwaysDoc{" +
               "description='" + description + '\'' +
               ", temporalOrderingTolerance=" + temporalOrderingTolerance +
               ", pathways=" + pathways +
               ", allowPathwayCreation=" + allowPathwayCreation +
               ", allowPathwayMutation=" + allowPathwayMutation +
               ", allowConstraintCreation=" + allowConstraintCreation +
               ", allowConstraintMutation=" + allowConstraintMutation +
               ", tracesDocRef=" + tracesDocRef +
               ", infoFeed=" + infoFeed +
               ", processingNode=" + processingNode +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<PathwaysDoc, PathwaysDoc.Builder> {

        private String description;
        private SimpleDuration temporalOrderingTolerance = new SimpleDuration(0, TimeUnit.NANOSECONDS);
        private List<Pathway> pathways;
        private boolean allowPathwayCreation = true;
        private boolean allowPathwayMutation = true;
        private boolean allowConstraintCreation = true;
        private boolean allowConstraintMutation = true;
        private DocRef tracesDocRef;
        private DocRef infoFeed;
        private String processingNode;

        private Builder() {
        }

        private Builder(final PathwaysDoc pathwaysDoc) {
            super(pathwaysDoc);
            this.description = pathwaysDoc.description;
            this.temporalOrderingTolerance = pathwaysDoc.temporalOrderingTolerance;
            this.pathways = pathwaysDoc.pathways;
            this.allowPathwayCreation = pathwaysDoc.allowPathwayCreation;
            this.allowPathwayMutation = pathwaysDoc.allowPathwayMutation;
            this.allowConstraintCreation = pathwaysDoc.allowConstraintCreation;
            this.allowConstraintMutation = pathwaysDoc.allowConstraintMutation;
            this.tracesDocRef = pathwaysDoc.tracesDocRef;
            this.infoFeed = pathwaysDoc.infoFeed;
            this.processingNode = pathwaysDoc.processingNode;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder temporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
            this.temporalOrderingTolerance = temporalOrderingTolerance;
            return self();
        }

        public Builder pathways(final List<Pathway> pathways) {
            this.pathways = pathways;
            return self();
        }

        public Builder allowPathwayCreation(final boolean allowPathwayCreation) {
            this.allowPathwayCreation = allowPathwayCreation;
            return self();
        }

        public Builder allowPathwayMutation(final boolean allowPathwayMutation) {
            this.allowPathwayMutation = allowPathwayMutation;
            return self();
        }

        public Builder allowConstraintCreation(final boolean allowConstraintCreation) {
            this.allowConstraintCreation = allowConstraintCreation;
            return self();
        }

        public Builder allowConstraintMutation(final boolean allowConstraintMutation) {
            this.allowConstraintMutation = allowConstraintMutation;
            return self();
        }

        public Builder tracesDocRef(final DocRef tracesDocRef) {
            this.tracesDocRef = tracesDocRef;
            return self();
        }

        public Builder infoFeed(final DocRef infoFeed) {
            this.infoFeed = infoFeed;
            return self();
        }

        public Builder processingNode(final String processingNode) {
            this.processingNode = processingNode;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PathwaysDoc build() {
            return new PathwaysDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    temporalOrderingTolerance,
                    pathways,
                    allowPathwayCreation = true,
                    allowPathwayMutation = true,
                    allowConstraintCreation = true,
                    allowConstraintMutation = true,
                    tracesDocRef,
                    infoFeed,
                    processingNode);
        }
    }
}
