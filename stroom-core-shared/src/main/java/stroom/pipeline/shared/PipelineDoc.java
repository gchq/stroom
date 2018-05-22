/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.docref.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * This entity is used to persist pipeline configuration.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "parentPipeline"})
@XmlRootElement(name = "pipeline")
@XmlType(name = "PipelineDoc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "parentPipeline"})
public class PipelineDoc extends Doc {
    private static final long serialVersionUID = 4519634323788508083L;

    public static final String DOCUMENT_TYPE = "Pipeline";

    @XmlElement(name = "description")
    private String description;
    @XmlElement(name = "parentPipeline")
    private DocRef parentPipeline;
    @XmlTransient
    @JsonIgnore
    private PipelineData pipelineData;

    public PipelineDoc() {
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final PipelineDoc that = (PipelineDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(parentPipeline, that.parentPipeline) &&
                Objects.equals(pipelineData, that.pipelineData);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), description, parentPipeline, pipelineData);
    }
}