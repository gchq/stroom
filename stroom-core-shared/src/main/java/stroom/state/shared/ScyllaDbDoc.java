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

package stroom.state.shared;

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

@Description(
        "Defines the connection details for a ScyllaDB state store instance.")
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
        "connection",
        "keyspace",
        "keyspaceCql"
})
@JsonInclude(Include.NON_NULL)
public class ScyllaDbDoc extends AbstractDoc {

    public static final String TYPE = "ScyllaDB";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.SCYLLA_DB_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private String connection;
    @JsonProperty
    private String keyspace;
    @JsonProperty
    private String keyspaceCql;

    @JsonCreator
    public ScyllaDbDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("connection") final String connection,
                       @JsonProperty("keyspace") final String keyspace,
                       @JsonProperty("keyspaceCql") final String keyspaceCql) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.connection = connection;
        this.keyspace = keyspace;
        this.keyspaceCql = keyspaceCql;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getConnection() {
        return connection;
    }

    public void setConnection(final String connection) {
        this.connection = connection;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(final String keyspace) {
        this.keyspace = keyspace;
    }

    public String getKeyspaceCql() {
        return keyspaceCql;
    }

    public void setKeyspaceCql(final String keyspaceCql) {
        this.keyspaceCql = keyspaceCql;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ScyllaDbDoc that = (ScyllaDbDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(connection, that.connection) &&
               Objects.equals(keyspace, that.keyspace) &&
               Objects.equals(keyspaceCql, that.keyspaceCql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, connection, keyspace, keyspaceCql);
    }

    @Override
    public String toString() {
        return "ScyllaDbDoc{" +
               "description='" + description + '\'' +
               ", connection='" + connection + '\'' +
               ", keyspace='" + keyspace + '\'' +
               ", keyspaceCql='" + keyspaceCql + '\'' +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ScyllaDbDoc, ScyllaDbDoc.Builder> {

        private String description;
        private String connection;
        private String keyspace;
        private String keyspaceCql;

        public Builder() {
        }

        public Builder(final ScyllaDbDoc scyllaDbDoc) {
            super(scyllaDbDoc);
            this.description = scyllaDbDoc.description;
            this.connection = scyllaDbDoc.connection;
            this.keyspace = scyllaDbDoc.keyspace;
            this.keyspaceCql = scyllaDbDoc.keyspaceCql;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder connection(final String connection) {
            this.connection = connection;
            return self();
        }

        public Builder keyspace(final String keyspace) {
            this.keyspace = keyspace;
            return self();
        }

        public Builder keyspaceCql(final String keyspaceCql) {
            this.keyspaceCql = keyspaceCql;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ScyllaDbDoc build() {
            return new ScyllaDbDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    connection,
                    keyspace,
                    keyspaceCql);
        }
    }
}
