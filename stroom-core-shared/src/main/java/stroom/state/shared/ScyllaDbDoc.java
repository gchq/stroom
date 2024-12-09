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
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "Defines the connection details for a ScyllaDB state store instance.")
@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
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

    public static final String DOCUMENT_TYPE = "ScyllaDB";
    public static final SvgImage ICON = SvgImage.DOCUMENT_SCYLLA_DB;

    @JsonProperty
    private String description;
    @JsonProperty
    private String connection;
    @JsonProperty
    private String keyspace;
    @JsonProperty
    private String keyspaceCql;

    public ScyllaDbDoc() {
    }

    @JsonCreator
    public ScyllaDbDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("uniqueName") final String uniqueName,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("connection") final String connection,
                       @JsonProperty("keyspace") final String keyspace,
                       @JsonProperty("keyspaceCql") final String keyspaceCql) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.connection = connection;
        this.keyspace = keyspace;
        this.keyspaceCql = keyspaceCql;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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
}
