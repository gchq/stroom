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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "text", propOrder = {
        "tableId",
        "streamIdField",
        "partNoField",
        "recordNoField",
        "lineFromField",
        "colFromField",
        "lineToField",
        "colToField",
        "pipeline",
        "showAsHtml",
        "showStepping",
        "modelVersion"
})
public class TextComponentSettings extends ComponentSettings {

    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "tableId")
    private String tableId;
    @XmlElement(name = "streamIdField")
    private Field streamIdField;
    @XmlElement(name = "partNoField")
    private Field partNoField;
    @XmlElement(name = "recordNoField")
    private Field recordNoField;
    @XmlElement(name = "lineFromField")
    private Field lineFromField;
    @XmlElement(name = "colFromField")
    private Field colFromField;
    @XmlElement(name = "lineToField")
    private Field lineToField;
    @XmlElement(name = "colToField")
    private Field colToField;
    @XmlElement(name = "pipeline")
    private DocRef pipeline;
    @XmlElement(name = "showAsHtml")
    private boolean showAsHtml;
    @XmlElement(name = "showStepping")
    private boolean showStepping;
    @XmlElement(name = "modelVersion")
    private String modelVersion;

    public TextComponentSettings() {
        // Default constructor necessary for GWT serialisation.
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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(final String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
