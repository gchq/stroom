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
import stroom.query.api.v2.Column;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private final Column streamIdField;
    @JsonProperty
    private final Column partNoField;
    @JsonProperty
    private final Column recordNoField;
    @JsonProperty
    private final Column lineFromField;
    @JsonProperty
    private final Column colFromField;
    @JsonProperty
    private final Column lineToField;
    @JsonProperty
    private final Column colToField;
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
                                 @JsonProperty("streamIdField") final Column streamIdField,
                                 @JsonProperty("partNoField") final Column partNoField,
                                 @JsonProperty("recordNoField") final Column recordNoField,
                                 @JsonProperty("lineFromField") final Column lineFromField,
                                 @JsonProperty("colFromField") final Column colFromField,
                                 @JsonProperty("lineToField") final Column lineToField,
                                 @JsonProperty("colToField") final Column colToField,
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

    @Deprecated
    public Column getStreamIdField() {
        return streamIdField;
    }

    @Deprecated
    public Column getPartNoField() {
        return partNoField;
    }

    @Deprecated
    public Column getRecordNoField() {
        return recordNoField;
    }

    @Deprecated
    public Column getLineFromField() {
        return lineFromField;
    }

    @Deprecated
    public Column getColFromField() {
        return colFromField;
    }

    @Deprecated
    public Column getLineToField() {
        return lineToField;
    }

    @Deprecated
    public Column getColToField() {
        return colToField;
    }

    @JsonIgnore
    public Column getStreamIdColumn() {
        return streamIdField;
    }

    @JsonIgnore
    public Column getPartNoColumn() {
        return partNoField;
    }

    @JsonIgnore
    public Column getRecordNoColumn() {
        return recordNoField;
    }

    @JsonIgnore
    public Column getLineFromColumn() {
        return lineFromField;
    }

    @JsonIgnore
    public Column getColFromColumn() {
        return colFromField;
    }

    @JsonIgnore
    public Column getLineToColumn() {
        return lineToField;
    }

    @JsonIgnore
    public Column getColToColumn() {
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

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder implements ComponentSettings.Builder {

        private String tableId;
        private Column streamIdField;
        private Column partNoField;
        private Column recordNoField;
        private Column lineFromField;
        private Column colFromField;
        private Column lineToField;
        private Column colToField;
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

        public Builder streamIdField(final Column streamIdField) {
            this.streamIdField = streamIdField;
            return this;
        }

        public Builder partNoField(final Column partNoField) {
            this.partNoField = partNoField;
            return this;
        }

        public Builder recordNoField(final Column recordNoField) {
            this.recordNoField = recordNoField;
            return this;
        }

        public Builder lineFromField(final Column lineFromField) {
            this.lineFromField = lineFromField;
            return this;
        }

        public Builder colFromField(final Column colFromField) {
            this.colFromField = colFromField;
            return this;
        }

        public Builder lineToField(final Column lineToField) {
            this.lineToField = lineToField;
            return this;
        }

        public Builder colToField(final Column colToField) {
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

        @Override
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
