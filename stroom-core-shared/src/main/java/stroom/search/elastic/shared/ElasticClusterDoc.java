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

package stroom.search.elastic.shared;

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
        "Defines the connection details for a single Elasticsearch cluster.\n" +
        "This Elastic Cluster Document can then be used by one or more Elastic Index Documents.")
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
        "connection"})
@JsonInclude(Include.NON_NULL)
public class ElasticClusterDoc extends AbstractDoc {

    public static final String TYPE = "ElasticCluster";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ELASTIC_CLUSTER_DOCUMENT_TYPE;

    @JsonProperty
    private String description;

    @JsonProperty
    private ElasticConnectionConfig connection;

    @JsonCreator
    public ElasticClusterDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("connection") final ElasticConnectionConfig connection) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.connection = connection;
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

    public ElasticConnectionConfig getConnection() {
        return connection;
    }

    public void setConnection(final ElasticConnectionConfig connection) {
        this.connection = connection;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElasticClusterDoc)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ElasticClusterDoc elasticCluster = (ElasticClusterDoc) o;
        return Objects.equals(description, elasticCluster.description) &&
               Objects.equals(connection, elasticCluster.connection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, connection);
    }

    @Override
    public String toString() {
        return "ElasticCluster{" +
               "description='" + description + '\'' +
               ", connectionConfig=" + connection +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<ElasticClusterDoc, ElasticClusterDoc.Builder> {

        private String description;
        private ElasticConnectionConfig connection = new ElasticConnectionConfig();

        private Builder() {
        }

        private Builder(final ElasticClusterDoc elasticClusterDoc) {
            super(elasticClusterDoc);
            this.description = elasticClusterDoc.description;
            this.connection = elasticClusterDoc.connection;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder connection(final ElasticConnectionConfig connection) {
            this.connection = connection;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ElasticClusterDoc build() {
            return new ElasticClusterDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    connection);
        }
    }
}
