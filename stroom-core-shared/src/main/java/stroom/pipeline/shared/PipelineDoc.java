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

package stroom.pipeline.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.pipeline.shared.data.PipelineData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * This entity is used to persist pipeline configuration.
 */
@Description("A Pipeline defines a chain of Pipeline elements that consumes from a source of data (a Stream of " +
             "raw data or cooked events) then processes it according to the elements used in the chain.\n" +
             "Pipelines can be linear or branching and support inheritance of other pipelines to allow re-use of " +
             "common structural parts.\n\n" +
             "The Pipeline Document defines the structure of the pipeline and the configuration of each of the " +
             "elements in that pipeline.\n" +
             "It also defines the filter(s) that will be used to control what data is passed through the pipeline " +
             "and the priority of processing.\n" +
             "The Pipeline Document can be used to view the data produced by the pipeline and to monitor its " +
             "processing state and progress." +
             "\n" +
             "{{% see-also %}}" +
             "[Pipelines]({{< relref \"docs/user-guide/pipelines\" >}})" +
             "{{% /see-also %}}")
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
        "parentPipeline",
        "pipelineData"})
@JsonInclude(Include.NON_NULL)
public class PipelineDoc extends AbstractDoc {

    public static final String TYPE = "Pipeline";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PIPELINE_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private DocRef parentPipeline;
    @JsonProperty
    private PipelineData pipelineData;

    @JsonCreator
    public PipelineDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("parentPipeline") final DocRef parentPipeline,
                       @JsonProperty("pipelineData") final PipelineData pipelineData) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.parentPipeline = parentPipeline;
        this.pipelineData = pipelineData;
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

    public DocRef getParentPipeline() {
        return parentPipeline;
    }

    public void setParentPipeline(final DocRef parentPipeline) {
        this.parentPipeline = parentPipeline;
    }

    public PipelineData getPipelineData() {
        return pipelineData;
    }

    public void setPipelineData(final PipelineData pipelineData) {
        this.pipelineData = pipelineData;
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
        final PipelineDoc that = (PipelineDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(parentPipeline, that.parentPipeline) &&
               Objects.equals(pipelineData, that.pipelineData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, parentPipeline, pipelineData);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<PipelineDoc, PipelineDoc.Builder> {

        private String description;
        private DocRef parentPipeline;
        private PipelineData pipelineData;

        private Builder() {
        }

        private Builder(final PipelineDoc pipelineDoc) {
            super(pipelineDoc);
            this.description = pipelineDoc.description;
            this.parentPipeline = pipelineDoc.parentPipeline;
            this.pipelineData = pipelineDoc.pipelineData;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder parentPipeline(final DocRef parentPipeline) {
            this.parentPipeline = parentPipeline;
            return self();
        }

        public Builder pipelineData(final PipelineData pipelineData) {
            this.pipelineData = pipelineData;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PipelineDoc build() {
            return new PipelineDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    parentPipeline,
                    pipelineData);
        }
    }
}
