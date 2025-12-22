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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.ColumnRef;

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
public final class TextComponentSettings implements ComponentSettings {

    @JsonProperty("tableId")
    private final String tableId;
    @JsonProperty
    private final ColumnRef streamIdField;
    @JsonProperty
    private final ColumnRef partNoField;
    @JsonProperty
    private final ColumnRef recordNoField;
    @JsonProperty
    private final ColumnRef lineFromField;
    @JsonProperty
    private final ColumnRef colFromField;
    @JsonProperty
    private final ColumnRef lineToField;
    @JsonProperty
    private final ColumnRef colToField;
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
                                 @JsonProperty("streamIdField") final ColumnRef streamIdField,
                                 @JsonProperty("partNoField") final ColumnRef partNoField,
                                 @JsonProperty("recordNoField") final ColumnRef recordNoField,
                                 @JsonProperty("lineFromField") final ColumnRef lineFromField,
                                 @JsonProperty("colFromField") final ColumnRef colFromField,
                                 @JsonProperty("lineToField") final ColumnRef lineToField,
                                 @JsonProperty("colToField") final ColumnRef colToField,
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
    public ColumnRef getStreamIdField() {
        return streamIdField;
    }

    @Deprecated
    public ColumnRef getPartNoField() {
        return partNoField;
    }

    @Deprecated
    public ColumnRef getRecordNoField() {
        return recordNoField;
    }

    @Deprecated
    public ColumnRef getLineFromField() {
        return lineFromField;
    }

    @Deprecated
    public ColumnRef getColFromField() {
        return colFromField;
    }

    @Deprecated
    public ColumnRef getLineToField() {
        return lineToField;
    }

    @Deprecated
    public ColumnRef getColToField() {
        return colToField;
    }

    @JsonIgnore
    public ColumnRef getStreamIdColumn() {
        return streamIdField;
    }

    @JsonIgnore
    public ColumnRef getPartNoColumn() {
        return partNoField;
    }

    @JsonIgnore
    public ColumnRef getRecordNoColumn() {
        return recordNoField;
    }

    @JsonIgnore
    public ColumnRef getLineFromColumn() {
        return lineFromField;
    }

    @JsonIgnore
    public ColumnRef getColFromColumn() {
        return colFromField;
    }

    @JsonIgnore
    public ColumnRef getLineToColumn() {
        return lineToField;
    }

    @JsonIgnore
    public ColumnRef getColToColumn() {
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

    public static final class Builder extends ComponentSettings
            .AbstractBuilder<TextComponentSettings, TextComponentSettings.Builder> {

        private String tableId;
        private ColumnRef streamIdField;
        private ColumnRef partNoField;
        private ColumnRef recordNoField;
        private ColumnRef lineFromField;
        private ColumnRef colFromField;
        private ColumnRef lineToField;
        private ColumnRef colToField;
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
            return self();
        }

        public Builder streamIdField(final ColumnRef streamIdField) {
            this.streamIdField = streamIdField;
            return self();
        }

        public Builder partNoField(final ColumnRef partNoField) {
            this.partNoField = partNoField;
            return self();
        }

        public Builder recordNoField(final ColumnRef recordNoField) {
            this.recordNoField = recordNoField;
            return self();
        }

        public Builder lineFromField(final ColumnRef lineFromField) {
            this.lineFromField = lineFromField;
            return self();
        }

        public Builder colFromField(final ColumnRef colFromField) {
            this.colFromField = colFromField;
            return self();
        }

        public Builder lineToField(final ColumnRef lineToField) {
            this.lineToField = lineToField;
            return self();
        }

        public Builder colToField(final ColumnRef colToField) {
            this.colToField = colToField;
            return self();
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return self();
        }

        public Builder showAsHtml(final boolean showAsHtml) {
            this.showAsHtml = showAsHtml;
            return self();
        }

        public Builder showStepping(final boolean showStepping) {
            this.showStepping = showStepping;
            return self();
        }

        public Builder modelVersion(final String modelVersion) {
            this.modelVersion = modelVersion;
            return self();
        }

        @Override
        protected Builder self() {
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
