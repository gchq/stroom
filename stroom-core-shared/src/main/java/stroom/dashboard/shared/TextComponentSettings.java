/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"tableId", "streamIdField", "partNoField", "recordNoField", "lineFromField", "colFromField", "lineToField", "colToField", "pipeline", "showAsHtml", "showStepping"})
@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "text")
@XmlType(name = "text", propOrder = {"tableId", "streamIdField", "partNoField", "recordNoField", "lineFromField", "colFromField", "lineToField", "colToField", "pipeline", "showAsHtml", "showStepping"})
public class TextComponentSettings extends ComponentSettings {
    @XmlElement(name = "tableId")
    @JsonProperty("tableId")
    private String tableId;
    @XmlElement(name = "streamIdField")
    @JsonProperty
    private Field streamIdField;
    @XmlElement(name = "partNoField")
    @JsonProperty
    private Field partNoField;
    @XmlElement(name = "recordNoField")
    @JsonProperty
    private Field recordNoField;
    @XmlElement(name = "lineFromField")
    @JsonProperty
    private Field lineFromField;
    @XmlElement(name = "colFromField")
    @JsonProperty
    private Field colFromField;
    @XmlElement(name = "lineToField")
    @JsonProperty
    private Field lineToField;
    @XmlElement(name = "colToField")
    @JsonProperty
    private Field colToField;
    @XmlElement(name = "pipeline")
    @JsonProperty("pipeline")
    private DocRef pipeline;
    @XmlElement(name = "showAsHtml")
    @JsonProperty("showAsHtml")
    private boolean showAsHtml;
    @XmlElement(name = "showStepping")
    @JsonProperty
    private boolean showStepping;

    public TextComponentSettings() {
        showStepping = true;
    }

    @JsonCreator
    public TextComponentSettings(@JsonProperty("tableId") final String tableId,
                                 @JsonProperty("streamIdField") final Field streamIdField,
                                 @JsonProperty("partNoField") final Field partNoField,
                                 @JsonProperty("recordNoField") final Field recordNoField,
                                 @JsonProperty("lineFromField") final Field lineFromField,
                                 @JsonProperty("colFromField") final Field colFromField,
                                 @JsonProperty("lineToField") final Field lineToField,
                                 @JsonProperty("colToField") final Field colToField,
                                 @JsonProperty("pipeline") final DocRef pipeline,
                                 @JsonProperty("showAsHtml") final boolean showAsHtml,
                                 @JsonProperty("showStepping") final boolean showStepping) {
        this.tableId = tableId;
        this.streamIdField = streamIdField;
        this.partNoField = partNoField;
        this.recordNoField = recordNoField;
        this.lineFromField = lineFromField;
        this.colFromField = colFromField;
        this.lineToField = lineToField;
        this.colToField = colToField;
        this.pipeline = pipeline;
        this.showAsHtml = showAsHtml;
        this.showStepping = showStepping;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(final String tableId) {
        this.tableId = tableId;
    }

    public Field getStreamIdField() {
        return streamIdField;
    }

    public void setStreamIdField(final Field streamIdField) {
        this.streamIdField = streamIdField;
    }

    public Field getPartNoField() {
        return partNoField;
    }

    public void setPartNoField(final Field partNoField) {
        this.partNoField = partNoField;
    }

    public Field getRecordNoField() {
        return recordNoField;
    }

    public void setRecordNoField(final Field recordNoField) {
        this.recordNoField = recordNoField;
    }

    public Field getLineFromField() {
        return lineFromField;
    }

    public void setLineFromField(final Field lineFromField) {
        this.lineFromField = lineFromField;
    }

    public Field getColFromField() {
        return colFromField;
    }

    public void setColFromField(final Field colFromField) {
        this.colFromField = colFromField;
    }

    public Field getLineToField() {
        return lineToField;
    }

    public void setLineToField(final Field lineToField) {
        this.lineToField = lineToField;
    }

    public Field getColToField() {
        return colToField;
    }

    public void setColToField(final Field colToField) {
        this.colToField = colToField;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public boolean isShowAsHtml() {
        return showAsHtml;
    }

    public void setShowAsHtml(boolean showAsHtml) {
        this.showAsHtml = showAsHtml;
    }

    public boolean isShowStepping() {
        return showStepping;
    }

    public void setShowStepping(final boolean showStepping) {
        this.showStepping = showStepping;
    }
}
