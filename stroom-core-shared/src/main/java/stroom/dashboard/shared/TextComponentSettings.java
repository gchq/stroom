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

import stroom.docref.DocRef;
import stroom.query.api.v2.Field;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
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
@JsonInclude(Include.NON_NULL)
public class TextComponentSettings implements ComponentSettings {

    @JsonProperty("tableId")
    private final String tableId;
    @JsonProperty
    private final Field streamIdField;
    @JsonProperty
    private final Field partNoField;
    @JsonProperty
    private final Field recordNoField;
    @JsonProperty
    private final Field lineFromField;
    @JsonProperty
    private final Field colFromField;
    @JsonProperty
    private final Field lineToField;
    @JsonProperty
    private final Field colToField;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final boolean showAsHtml;
    @JsonProperty
    private final boolean showStepping;
    @JsonProperty
    private final String modelVersion;

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
                                 @JsonProperty("showStepping") final boolean showStepping,
                                 @JsonProperty("modelVersion") final String modelVersion) {
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
        this.modelVersion = modelVersion;
    }

    public String getTableId() {
        return tableId;
    }

    public Field getStreamIdField() {
        return streamIdField;
    }

    public Field getPartNoField() {
        return partNoField;
    }

    public Field getRecordNoField() {
        return recordNoField;
    }

    public Field getLineFromField() {
        return lineFromField;
    }

    public Field getColFromField() {
        return colFromField;
    }

    public Field getLineToField() {
        return lineToField;
    }

    public Field getColToField() {
        return colToField;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public boolean isShowAsHtml() {
        return showAsHtml;
    }

    public boolean isShowStepping() {
        return showStepping;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TextComponentSettings that = (TextComponentSettings) o;
        return showAsHtml == that.showAsHtml &&
                showStepping == that.showStepping &&
                Objects.equals(tableId, that.tableId) &&
                Objects.equals(streamIdField, that.streamIdField) &&
                Objects.equals(partNoField, that.partNoField) &&
                Objects.equals(recordNoField, that.recordNoField) &&
                Objects.equals(lineFromField, that.lineFromField) &&
                Objects.equals(colFromField, that.colFromField) &&
                Objects.equals(lineToField, that.lineToField) &&
                Objects.equals(colToField, that.colToField) &&
                Objects.equals(pipeline, that.pipeline) &&
                Objects.equals(modelVersion, that.modelVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId,
                streamIdField,
                partNoField,
                recordNoField,
                lineFromField,
                colFromField,
                lineToField,
                colToField,
                pipeline,
                showAsHtml,
                showStepping,
                modelVersion);
    }

    @Override
    public String toString() {
        return "TextComponentSettings{" +
                "tableId='" + tableId + '\'' +
                ", streamIdField=" + streamIdField +
                ", partNoField=" + partNoField +
                ", recordNoField=" + recordNoField +
                ", lineFromField=" + lineFromField +
                ", colFromField=" + colFromField +
                ", lineToField=" + lineToField +
                ", colToField=" + colToField +
                ", pipeline=" + pipeline +
                ", showAsHtml=" + showAsHtml +
                ", showStepping=" + showStepping +
                ", modelVersion='" + modelVersion + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String tableId;
        private Field streamIdField;
        private Field partNoField;
        private Field recordNoField;
        private Field lineFromField;
        private Field colFromField;
        private Field lineToField;
        private Field colToField;
        private DocRef pipeline;
        private boolean showAsHtml;
        private boolean showStepping;
        private String modelVersion;

        private Builder() {
        }

        private Builder(final TextComponentSettings textComponentSettings) {
            this.tableId = textComponentSettings.tableId;
            this.streamIdField = textComponentSettings.streamIdField;
            this.partNoField = textComponentSettings.partNoField;
            this.recordNoField = textComponentSettings.recordNoField;
            this.lineFromField = textComponentSettings.lineFromField;
            this.colFromField = textComponentSettings.colFromField;
            this.lineToField = textComponentSettings.lineToField;
            this.colToField = textComponentSettings.colToField;
            this.pipeline = textComponentSettings.pipeline;
            this.showAsHtml = textComponentSettings.showAsHtml;
            this.showStepping = textComponentSettings.showStepping;
            this.modelVersion = textComponentSettings.modelVersion;
        }

        public Builder tableId(final String tableId) {
            this.tableId = tableId;
            return this;
        }

        public Builder streamIdField(final Field streamIdField) {
            this.streamIdField = streamIdField;
            return this;
        }

        public Builder partNoField(final Field partNoField) {
            this.partNoField = partNoField;
            return this;
        }

        public Builder recordNoField(final Field recordNoField) {
            this.recordNoField = recordNoField;
            return this;
        }

        public Builder lineFromField(final Field lineFromField) {
            this.lineFromField = lineFromField;
            return this;
        }

        public Builder colFromField(final Field colFromField) {
            this.colFromField = colFromField;
            return this;
        }

        public Builder lineToField(final Field lineToField) {
            this.lineToField = lineToField;
            return this;
        }

        public Builder colToField(final Field colToField) {
            this.colToField = colToField;
            return this;
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder showAsHtml(final boolean showAsHtml) {
            this.showAsHtml = showAsHtml;
            return this;
        }

        public Builder showStepping(final boolean showStepping) {
            this.showStepping = showStepping;
            return this;
        }

        public Builder modelVersion(final String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public TextComponentSettings build() {
            return new TextComponentSettings(
                    tableId,
                    streamIdField,
                    partNoField,
                    recordNoField,
                    lineFromField,
                    colFromField,
                    lineToField,
                    colToField,
                    pipeline,
                    showAsHtml,
                    showStepping,
                    modelVersion
            );
        }
    }
}
