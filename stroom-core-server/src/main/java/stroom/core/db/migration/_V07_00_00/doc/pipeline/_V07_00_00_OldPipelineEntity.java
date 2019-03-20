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

package stroom.core.db.migration._V07_00_00.doc.pipeline;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_DocumentEntity;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_ExternalFile;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_SQLNameConstants;

import javax.xml.bind.annotation.XmlTransient;

/**
 * This entity is used to persist pipeline configuration.
 */
public class _V07_00_00_OldPipelineEntity extends _V07_00_00_DocumentEntity {
    public static final String TABLE_NAME = _V07_00_00_SQLNameConstants.PIPELINE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String PARENT_PIPELINE = _V07_00_00_SQLNameConstants.PARENT + SEP + _V07_00_00_SQLNameConstants.PIPELINE;
    public static final String DATA = _V07_00_00_SQLNameConstants.DATA;

    public static final String ENTITY_TYPE = "Pipeline";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;

    private String parentPipelineXML;
    private _V07_00_00_DocRef parentPipeline;

    private String data;
    private _V07_00_00_PipelineData pipelineData;

    public _V07_00_00_OldPipelineEntity() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getParentPipelineXML() {
        return parentPipelineXML;
    }

    public void setParentPipelineXML(final String parentPipelineXML) {
        this.parentPipelineXML = parentPipelineXML;
    }

    @XmlTransient
    public _V07_00_00_DocRef getParentPipeline() {
        return parentPipeline;
    }

    public void setParentPipeline(final _V07_00_00_DocRef parentPipeline) {
        this.parentPipeline = parentPipeline;
    }

    @_V07_00_00_ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @XmlTransient
    public _V07_00_00_PipelineData getPipelineData() {
        return pipelineData;
    }

    public void setPipelineData(final _V07_00_00_PipelineData pipelineData) {
        this.pipelineData = pipelineData;
    }

    /**
     * @return generic UI drop down value
     */
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
