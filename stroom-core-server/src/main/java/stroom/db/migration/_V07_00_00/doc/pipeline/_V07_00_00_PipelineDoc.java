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

package stroom.db.migration._V07_00_00.doc.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;

import java.util.Objects;

/**
 * This entity is used to persist pipeline configuration.
 */
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "parentPipeline"})
public class _V07_00_00_PipelineDoc extends _V07_00_00_Doc {
    private static final long serialVersionUID = 4519634323788508083L;

    public static final String DOCUMENT_TYPE = "Pipeline";

    private String description;
    private _V07_00_00_DocRef parentPipeline;
    @JsonIgnore
    private _V07_00_00_PipelineData pipelineData;

    public _V07_00_00_PipelineDoc() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public _V07_00_00_DocRef getParentPipeline() {
        return parentPipeline;
    }

    public void setParentPipeline(final _V07_00_00_DocRef parentPipeline) {
        this.parentPipeline = parentPipeline;
    }

    public _V07_00_00_PipelineData getPipelineData() {
        return pipelineData;
    }

    public void setPipelineData(final _V07_00_00_PipelineData pipelineData) {
        this.pipelineData = pipelineData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final _V07_00_00_PipelineDoc that = (_V07_00_00_PipelineDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(parentPipeline, that.parentPipeline) &&
                Objects.equals(pipelineData, that.pipelineData);
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), description, parentPipeline, pipelineData);
    }
}