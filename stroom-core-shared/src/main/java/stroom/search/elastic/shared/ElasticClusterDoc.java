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

package stroom.search.elastic.shared;

import stroom.docstore.shared.Doc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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
public class ElasticClusterDoc extends Doc {

    public static final String DOCUMENT_TYPE = "ElasticCluster";

    @JsonProperty
    private String description;

    @JsonProperty
    private ElasticConnectionConfig connection;

    public ElasticClusterDoc() {
        this.connection = new ElasticConnectionConfig();
    }

    @JsonCreator
    public ElasticClusterDoc(@JsonProperty("type") final String type,
                             @JsonProperty("uuid") final String uuid,
                             @JsonProperty("name") final String name,
                             @JsonProperty("version") final String version,
                             @JsonProperty("createTimeMs") final Long createTimeMs,
                             @JsonProperty("updateTimeMs") final Long updateTimeMs,
                             @JsonProperty("createUser") final String createUser,
                             @JsonProperty("updateUser") final String updateUser,
                             @JsonProperty("description") final String description,
                             @JsonProperty("connection") final ElasticConnectionConfig connection) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.connection = connection;
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

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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
}
