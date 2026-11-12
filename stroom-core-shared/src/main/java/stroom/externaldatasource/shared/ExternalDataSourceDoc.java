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

package stroom.externaldatasource.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Data from outside Stroom that StroomQL and dashboard queries can use as a data source.
///
/// For a JDBC database or Amazon Athena the document holds the connection but no SQL. Each query
/// carries its own SQL in [stroom.query.api.Query#getNativeQuery()], and the columns of its result
/// become the query's fields. For an LDAP directory each query carries its own search filter there,
/// and the attributes the document lists are the fields. For CSV and Excel the document holds the data
/// itself, and its columns are the fields.
@Description(
        "Defines data from outside Stroom that queries can use: a JDBC database or Amazon Athena, " +
        "queried with SQL given in each query, an LDAP directory, searched with a filter given in each " +
        "query, or CSV or an Excel workbook held in this document.")
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "sourceType",
        "jdbc",
        "athena",
        "csv",
        "excel",
        "ldap",
        "timeField",
        "queryTimeoutSeconds",
        "maxRows"})
@JsonInclude(Include.NON_NULL)
public class ExternalDataSourceDoc extends AbstractDoc {

    public static final String TYPE = "ExternalDataSource";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.EXTERNAL_DATA_SOURCE_DOCUMENT_TYPE;

    /// The longest a query may run, in seconds, when no timeout is set.
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;
    /// The most rows a query may return when no limit is set.
    public static final int DEFAULT_MAX_ROWS = 100_000;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final ExternalDataSourceType sourceType;
    @JsonProperty
    private final JdbcConnectionSettings jdbc;
    @JsonProperty
    private final AthenaConnectionSettings athena;
    @JsonProperty
    private final CsvSettings csv;
    @JsonProperty
    private final ExcelSettings excel;
    @JsonProperty
    private final LdapSettings ldap;
    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final Integer queryTimeoutSeconds;
    @JsonProperty
    private final Integer maxRows;

    /// Creates an external data source document.
    ///
    /// @param uuid                The document's UUID.
    /// @param name                The document's name.
    /// @param version             The document's version.
    /// @param createTimeMs        When the document was created.
    /// @param updateTimeMs        When the document was last updated.
    /// @param createUser          Who created the document.
    /// @param updateUser          Who last updated the document.
    /// @param description         A description of the data source, in markdown.
    /// @param sourceType          The type of data source, or `null` for [ExternalDataSourceType#JDBC].
    /// @param jdbc                The settings used when the type is JDBC.
    /// @param athena              The settings used when the type is Athena.
    /// @param csv                 The settings and data used when the type is CSV.
    /// @param excel               The settings used when the type is Excel.
    /// @param ldap                The settings used when the type is LDAP.
    /// @param timeField           The column used as the time field, or `null` for none. Used by JDBC,
    ///                            LDAP, CSV and Excel.
    /// @param queryTimeoutSeconds The longest a query may run, or `null` for the default.
    /// @param maxRows             The most rows a query may return, or `null` for the default.
    @JsonCreator
    public ExternalDataSourceDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("sourceType") final ExternalDataSourceType sourceType,
            @JsonProperty("jdbc") final JdbcConnectionSettings jdbc,
            @JsonProperty("athena") final AthenaConnectionSettings athena,
            @JsonProperty("csv") final CsvSettings csv,
            @JsonProperty("excel") final ExcelSettings excel,
            @JsonProperty("ldap") final LdapSettings ldap,
            @JsonProperty("timeField") final String timeField,
            @JsonProperty("queryTimeoutSeconds") final Integer queryTimeoutSeconds,
            @JsonProperty("maxRows") final Integer maxRows) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.sourceType = sourceType;
        this.jdbc = jdbc;
        this.athena = athena;
        this.csv = csv;
        this.excel = excel;
        this.ldap = ldap;
        this.timeField = timeField;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.maxRows = maxRows;
    }

    /// @return A new builder for creating a [DocRef] for this document's type.
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    /// @return A description of the data source, in markdown, or `null` for none.
    public String getDescription() {
        return description;
    }

    /// @return The type of data source, or `null` meaning [ExternalDataSourceType#JDBC].
    public ExternalDataSourceType getSourceType() {
        return sourceType;
    }

    /// @return The settings used when the type is JDBC, or `null` if not set.
    public JdbcConnectionSettings getJdbc() {
        return jdbc;
    }

    /// @return The settings used when the type is Athena, or `null` if not set.
    public AthenaConnectionSettings getAthena() {
        return athena;
    }

    /// @return The settings and data used when the type is CSV, or `null` if not set.
    public CsvSettings getCsv() {
        return csv;
    }

    /// @return The settings used when the type is Excel, or `null` if not set.
    public ExcelSettings getExcel() {
        return excel;
    }

    /// @return The settings used when the type is LDAP, or `null` if not set.
    public LdapSettings getLdap() {
        return ldap;
    }

    /// @return The column used as the time field, or `null` for none. Used by JDBC, LDAP, CSV and Excel.
    public String getTimeField() {
        return timeField;
    }

    /// @return The longest a query may run, in seconds, or `null` to use
    /// [#DEFAULT_QUERY_TIMEOUT_SECONDS].
    public Integer getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    /// @return The most rows a query may return, or `null` to use [#DEFAULT_MAX_ROWS].
    public Integer getMaxRows() {
        return maxRows;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final ExternalDataSourceDoc that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(description, that.description) &&
               sourceType == that.sourceType &&
               Objects.equals(jdbc, that.jdbc) &&
               Objects.equals(athena, that.athena) &&
               Objects.equals(csv, that.csv) &&
               Objects.equals(excel, that.excel) &&
               Objects.equals(ldap, that.ldap) &&
               Objects.equals(timeField, that.timeField) &&
               Objects.equals(queryTimeoutSeconds, that.queryTimeoutSeconds) &&
               Objects.equals(maxRows, that.maxRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                sourceType,
                jdbc,
                athena,
                csv,
                excel,
                ldap,
                timeField,
                queryTimeoutSeconds,
                maxRows);
    }

    @Override
    public String toString() {
        return "ExternalDataSourceDoc{" +
               "description='" + description + '\'' +
               ", sourceType=" + sourceType +
               ", jdbc=" + jdbc +
               ", athena=" + athena +
               ", csv=" + csv +
               ", excel=" + excel +
               ", ldap=" + ldap +
               ", timeField='" + timeField + '\'' +
               ", queryTimeoutSeconds=" + queryTimeoutSeconds +
               ", maxRows=" + maxRows +
               '}';
    }

    /// @return A builder initialised with the values of this document.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new builder holding the defaults for a new document.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [ExternalDataSourceDoc] instances.
    public static final class Builder extends AbstractBuilder<ExternalDataSourceDoc, Builder> {

        private String description;
        private ExternalDataSourceType sourceType = ExternalDataSourceType.JDBC;
        private JdbcConnectionSettings jdbc;
        private AthenaConnectionSettings athena;
        private CsvSettings csv;
        private ExcelSettings excel;
        private LdapSettings ldap;
        private String timeField;
        private Integer queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;
        private Integer maxRows = DEFAULT_MAX_ROWS;

        private Builder() {
        }

        private Builder(final ExternalDataSourceDoc doc) {
            super(doc);
            this.description = doc.description;
            this.sourceType = doc.sourceType;
            this.jdbc = doc.jdbc;
            this.athena = doc.athena;
            this.csv = doc.csv;
            this.excel = doc.excel;
            this.ldap = doc.ldap;
            this.timeField = doc.timeField;
            this.queryTimeoutSeconds = doc.queryTimeoutSeconds;
            this.maxRows = doc.maxRows;
        }

        /// @param description A description of the data source, in markdown.
        /// @return This builder.
        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        /// @param sourceType The type of data source.
        /// @return This builder.
        public Builder sourceType(final ExternalDataSourceType sourceType) {
            this.sourceType = sourceType;
            return self();
        }

        /// @param jdbc The settings used when the type is JDBC.
        /// @return This builder.
        public Builder jdbc(final JdbcConnectionSettings jdbc) {
            this.jdbc = jdbc;
            return self();
        }

        /// @param athena The settings used when the type is Athena.
        /// @return This builder.
        public Builder athena(final AthenaConnectionSettings athena) {
            this.athena = athena;
            return self();
        }

        /// @param csv The settings and data used when the type is CSV.
        /// @return This builder.
        public Builder csv(final CsvSettings csv) {
            this.csv = csv;
            return self();
        }

        /// @param excel The settings used when the type is Excel.
        /// @return This builder.
        public Builder excel(final ExcelSettings excel) {
            this.excel = excel;
            return self();
        }

        /// @param ldap The settings used when the type is LDAP.
        /// @return This builder.
        public Builder ldap(final LdapSettings ldap) {
            this.ldap = ldap;
            return self();
        }

        /// @param timeField The column used as the time field, or `null` for none.
        /// @return This builder.
        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return self();
        }

        /// @param queryTimeoutSeconds The longest a query may run, or `null` for the default.
        /// @return This builder.
        public Builder queryTimeoutSeconds(final Integer queryTimeoutSeconds) {
            this.queryTimeoutSeconds = queryTimeoutSeconds;
            return self();
        }

        /// @param maxRows The most rows a query may return, or `null` for the default.
        /// @return This builder.
        public Builder maxRows(final Integer maxRows) {
            this.maxRows = maxRows;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        /// @return A new [ExternalDataSourceDoc] holding the values set on this builder.
        public ExternalDataSourceDoc build() {
            return new ExternalDataSourceDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    sourceType,
                    jdbc,
                    athena,
                    csv,
                    excel,
                    ldap,
                    timeField,
                    queryTimeoutSeconds,
                    maxRows);
        }
    }
}
