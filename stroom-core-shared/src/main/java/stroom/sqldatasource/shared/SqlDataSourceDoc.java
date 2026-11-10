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

package stroom.sqldatasource.shared;

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

/// A connection to an external database that StroomQL and dashboard queries can use as a data source.
///
/// The document holds no SQL. Each query carries its own SQL in
/// [stroom.query.api.Query#getNativeQuery()], and the columns of its result become the query's fields.
@Description(
        "Defines a connection to an external SQL database, such as a JDBC database or Amazon Athena.\n" +
        "Queries name this document as their data source and supply the SQL to run against it.")
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
        "connectionType",
        "jdbc",
        "athena",
        "timeField",
        "queryTimeoutSeconds",
        "maxRows"})
@JsonInclude(Include.NON_NULL)
public class SqlDataSourceDoc extends AbstractDoc {

    public static final String TYPE = "SqlDataSource";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.SQL_DATA_SOURCE_DOCUMENT_TYPE;

    /// The longest a query may run, in seconds, when no timeout is set.
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;
    /// The most rows a query may return when no limit is set.
    public static final int DEFAULT_MAX_ROWS = 100_000;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final SqlConnectionType connectionType;
    @JsonProperty
    private final JdbcConnectionSettings jdbc;
    @JsonProperty
    private final AthenaConnectionSettings athena;
    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final Integer queryTimeoutSeconds;
    @JsonProperty
    private final Integer maxRows;

    /// Creates a SQL data source document.
    ///
    /// @param uuid                The document's UUID.
    /// @param name                The document's name.
    /// @param version             The document's version.
    /// @param createTimeMs        When the document was created.
    /// @param updateTimeMs        When the document was last updated.
    /// @param createUser          Who created the document.
    /// @param updateUser          Who last updated the document.
    /// @param description         A description of the data source, in markdown.
    /// @param connectionType      How the data source connects, or `null` for [SqlConnectionType#JDBC].
    /// @param jdbc                The settings used when the connection type is JDBC.
    /// @param athena              The settings used when the connection type is Athena.
    /// @param timeField           The result column used as the time field, or `null` for none.
    /// @param queryTimeoutSeconds The longest a query may run, or `null` for the default.
    /// @param maxRows             The most rows a query may return, or `null` for the default.
    @JsonCreator
    public SqlDataSourceDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("connectionType") final SqlConnectionType connectionType,
            @JsonProperty("jdbc") final JdbcConnectionSettings jdbc,
            @JsonProperty("athena") final AthenaConnectionSettings athena,
            @JsonProperty("timeField") final String timeField,
            @JsonProperty("queryTimeoutSeconds") final Integer queryTimeoutSeconds,
            @JsonProperty("maxRows") final Integer maxRows) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.connectionType = connectionType;
        this.jdbc = jdbc;
        this.athena = athena;
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

    /// @return How the data source connects, or `null` meaning [SqlConnectionType#JDBC].
    public SqlConnectionType getConnectionType() {
        return connectionType;
    }

    /// @return The settings used when the connection type is JDBC, or `null` if not set.
    public JdbcConnectionSettings getJdbc() {
        return jdbc;
    }

    /// @return The settings used when the connection type is Athena, or `null` if not set.
    public AthenaConnectionSettings getAthena() {
        return athena;
    }

    /// @return The result column used as the time field, or `null` for none.
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
        if (!(o instanceof final SqlDataSourceDoc that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(description, that.description) &&
               connectionType == that.connectionType &&
               Objects.equals(jdbc, that.jdbc) &&
               Objects.equals(athena, that.athena) &&
               Objects.equals(timeField, that.timeField) &&
               Objects.equals(queryTimeoutSeconds, that.queryTimeoutSeconds) &&
               Objects.equals(maxRows, that.maxRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                connectionType,
                jdbc,
                athena,
                timeField,
                queryTimeoutSeconds,
                maxRows);
    }

    @Override
    public String toString() {
        return "SqlDataSourceDoc{" +
               "description='" + description + '\'' +
               ", connectionType=" + connectionType +
               ", jdbc=" + jdbc +
               ", athena=" + athena +
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

    /// Builds [SqlDataSourceDoc] instances.
    public static final class Builder extends AbstractBuilder<SqlDataSourceDoc, Builder> {

        private String description;
        private SqlConnectionType connectionType = SqlConnectionType.JDBC;
        private JdbcConnectionSettings jdbc;
        private AthenaConnectionSettings athena;
        private String timeField;
        private Integer queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;
        private Integer maxRows = DEFAULT_MAX_ROWS;

        private Builder() {
        }

        private Builder(final SqlDataSourceDoc doc) {
            super(doc);
            this.description = doc.description;
            this.connectionType = doc.connectionType;
            this.jdbc = doc.jdbc;
            this.athena = doc.athena;
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

        /// @param connectionType How the data source connects.
        /// @return This builder.
        public Builder connectionType(final SqlConnectionType connectionType) {
            this.connectionType = connectionType;
            return self();
        }

        /// @param jdbc The settings used when the connection type is JDBC.
        /// @return This builder.
        public Builder jdbc(final JdbcConnectionSettings jdbc) {
            this.jdbc = jdbc;
            return self();
        }

        /// @param athena The settings used when the connection type is Athena.
        /// @return This builder.
        public Builder athena(final AthenaConnectionSettings athena) {
            this.athena = athena;
            return self();
        }

        /// @param timeField The result column used as the time field, or `null` for none.
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

        /// @return A new [SqlDataSourceDoc] holding the values set on this builder.
        public SqlDataSourceDoc build() {
            return new SqlDataSourceDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    connectionType,
                    jdbc,
                    athena,
                    timeField,
                    queryTimeoutSeconds,
                    maxRows);
        }
    }
}
