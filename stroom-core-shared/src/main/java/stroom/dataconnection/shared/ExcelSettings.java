/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.shared;

import stroom.util.shared.ResourceKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// The settings of an Excel [DataConnectionDoc], whose workbook is held in the document.
///
/// The workbook is stored apart from the document's JSON, in its own `xlsx` part, and never goes to
/// the browser: [#getData()] is not written as JSON. A workbook is uploaded with the upload servlet,
/// and its [ResourceKey] sent with the settings; saving the document stores it. Saving a copy of a
/// document names the document to copy the workbook from instead. Without either, saving keeps the
/// workbook already stored.
@JsonPropertyOrder({
        "fileName",
        "sheet",
        "header",
        "columns",
        "uploadKey",
        "copyWorkbookFrom"})
@JsonInclude(Include.NON_NULL)
public class ExcelSettings implements StoredDataSettings {

    @JsonProperty
    private final String fileName;
    @JsonProperty
    private final String sheet;
    @JsonProperty
    private final Boolean header;
    @JsonProperty
    private final List<DataColumn> columns;
    @JsonProperty
    private final ResourceKey uploadKey;
    @JsonProperty
    private final String copyWorkbookFrom;
    @JsonIgnore
    private final byte[] data;

    /// Creates Excel settings from JSON, which never holds the workbook.
    ///
    /// @param fileName  The name of the uploaded workbook file, or `null` for none.
    /// @param sheet     The sheet to read, or `null` for the first.
    /// @param header    True if the first row holds the column names, `null` meaning true.
    /// @param columns   The columns, in the order they appear in each row, or `null` for none.
    /// @param uploadKey A workbook uploaded but not yet saved with the document, or `null`.
    /// @param copyWorkbookFrom The UUID of a document to copy the workbook from when saving a copy, or
    ///                         `null`.
    @JsonCreator
    public ExcelSettings(@JsonProperty("fileName") final String fileName,
                         @JsonProperty("sheet") final String sheet,
                         @JsonProperty("header") final Boolean header,
                         @JsonProperty("columns") final List<DataColumn> columns,
                         @JsonProperty("uploadKey") final ResourceKey uploadKey,
                         @JsonProperty("copyWorkbookFrom") final String copyWorkbookFrom) {
        this(fileName, sheet, header, columns, uploadKey, copyWorkbookFrom, null);
    }

    private ExcelSettings(final String fileName,
                          final String sheet,
                          final Boolean header,
                          final List<DataColumn> columns,
                          final ResourceKey uploadKey,
                          final String copyWorkbookFrom,
                          final byte[] data) {
        this.fileName = fileName;
        this.sheet = sheet;
        this.header = header;
        this.columns = columns;
        this.uploadKey = uploadKey;
        this.copyWorkbookFrom = copyWorkbookFrom;
        this.data = data;
    }

    /// @return The name of the uploaded workbook file, or `null` for none.
    public String getFileName() {
        return fileName;
    }

    /// @return The sheet to read, or `null` for the first.
    public String getSheet() {
        return sheet;
    }

    @Override
    public Boolean getHeader() {
        return header;
    }

    @Override
    public List<DataColumn> getColumns() {
        return columns;
    }

    /// @return A workbook uploaded but not yet saved with the document, or `null`.
    public ResourceKey getUploadKey() {
        return uploadKey;
    }

    /// @return The UUID of a document to copy the workbook from when saving a copy, or `null`.
    public String getCopyWorkbookFrom() {
        return copyWorkbookFrom;
    }

    /// @return The stored workbook, on the server only, or `null` for none.
    @JsonIgnore
    public byte[] getData() {
        return data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final ExcelSettings that)) {
            return false;
        }
        return Objects.equals(fileName, that.fileName) &&
               Objects.equals(sheet, that.sheet) &&
               Objects.equals(header, that.header) &&
               Objects.equals(columns, that.columns) &&
               Objects.equals(uploadKey, that.uploadKey) &&
               Objects.equals(copyWorkbookFrom, that.copyWorkbookFrom) &&
               Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, sheet, header, columns, uploadKey, copyWorkbookFrom, Arrays.hashCode(data));
    }

    // The workbook may be large, so only its length is shown.
    @Override
    public String toString() {
        return "ExcelSettings{" +
               "fileName='" + fileName + '\'' +
               ", sheet='" + sheet + '\'' +
               ", header=" + header +
               ", columns=" + columns +
               ", uploadKey=" + uploadKey +
               ", copyWorkbookFrom='" + copyWorkbookFrom + '\'' +
               ", dataLength=" + (data == null ? 0 : data.length) +
               '}';
    }

    /// @return A builder initialised with these settings.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new builder with no settings.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [ExcelSettings] instances.
    public static final class Builder {

        private String fileName;
        private String sheet;
        private Boolean header;
        private List<DataColumn> columns;
        private ResourceKey uploadKey;
        private String copyWorkbookFrom;
        private byte[] data;

        private Builder() {
        }

        private Builder(final ExcelSettings settings) {
            this.fileName = settings.fileName;
            this.sheet = settings.sheet;
            this.header = settings.header;
            this.columns = settings.columns;
            this.uploadKey = settings.uploadKey;
            this.copyWorkbookFrom = settings.copyWorkbookFrom;
            this.data = settings.data;
        }

        /// @param fileName The name of the uploaded workbook file.
        /// @return This builder.
        public Builder fileName(final String fileName) {
            this.fileName = fileName;
            return this;
        }

        /// @param sheet The sheet to read, or `null` for the first.
        /// @return This builder.
        public Builder sheet(final String sheet) {
            this.sheet = sheet;
            return this;
        }

        /// @param header True if the first row holds the column names.
        /// @return This builder.
        public Builder header(final Boolean header) {
            this.header = header;
            return this;
        }

        /// @param columns The columns, in the order they appear in each row.
        /// @return This builder.
        public Builder columns(final List<DataColumn> columns) {
            this.columns = columns;
            return this;
        }

        /// @param uploadKey A workbook uploaded but not yet saved with the document, or `null`.
        /// @return This builder.
        public Builder uploadKey(final ResourceKey uploadKey) {
            this.uploadKey = uploadKey;
            return this;
        }

        /// @param copyWorkbookFrom The UUID of a document to copy the workbook from, or `null`.
        /// @return This builder.
        public Builder copyWorkbookFrom(final String copyWorkbookFrom) {
            this.copyWorkbookFrom = copyWorkbookFrom;
            return this;
        }

        /// @param data The stored workbook, on the server only.
        /// @return This builder.
        public Builder data(final byte[] data) {
            this.data = data;
            return this;
        }

        /// @return New settings holding the values set on this builder.
        public ExcelSettings build() {
            return new ExcelSettings(fileName, sheet, header, columns, uploadKey, copyWorkbookFrom, data);
        }
    }
}
