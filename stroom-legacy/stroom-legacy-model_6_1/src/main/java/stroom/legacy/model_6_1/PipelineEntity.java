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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlTransient;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * This entity is used to persist pipeline configuration.
 */
@Entity
@Table(name = "PIPE")
@Deprecated
public class PipelineEntity extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.PIPELINE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String PARENT_PIPELINE = SQLNameConstants.PARENT + SEP + SQLNameConstants.PIPELINE;
    public static final String DATA = SQLNameConstants.DATA;

    public static final String ENTITY_TYPE = "Pipeline";
    public static final String STEPPING_PERMISSION = "Pipeline Stepping";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;

    private String parentPipelineXML;
    private DocRef parentPipeline;

    private String data;
    private PipelineData pipelineData;

    public PipelineEntity() {
    }

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = PARENT_PIPELINE)
    @Lob
    public String getParentPipelineXML() {
        return parentPipelineXML;
    }

    public void setParentPipelineXML(final String parentPipelineXML) {
        this.parentPipelineXML = parentPipelineXML;
    }

    @Transient
    @XmlTransient
    public DocRef getParentPipeline() {
        return parentPipeline;
    }

    public void setParentPipeline(final DocRef parentPipeline) {
        this.parentPipeline = parentPipeline;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Transient
    @XmlTransient
    public PipelineData getPipelineData() {
        return pipelineData;
    }

    public void setPipelineData(final PipelineData pipelineData) {
        this.pipelineData = pipelineData;
    }

    /**
     * @return generic UI drop down value
     */
    @Transient
    @Override
    public String getDisplayValue() {
        return String.valueOf(getName());
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
