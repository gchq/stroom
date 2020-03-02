/*
 * Copyright 2016 Crown Copyright
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

package stroom.xmlschema.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.util.shared.HasData;

import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "namespaceURI", "data", "systemId", "deprecated", "schemaGroup"})
@JsonInclude(Include.NON_DEFAULT)
public class XmlSchemaDoc extends Doc implements HasData {
    public static final String DOCUMENT_TYPE = "XMLSchema";

    @JsonProperty
    private String description;
    @JsonProperty
    private String namespaceURI;
    @JsonProperty
    private String systemId;
    @JsonProperty
    private String data;
    @JsonProperty
    private boolean deprecated;
    @JsonProperty
    private String schemaGroup;

    public XmlSchemaDoc() {
    }

    @JsonCreator
    public XmlSchemaDoc(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("version") final String version,
                        @JsonProperty("createTime") final Long createTime,
                        @JsonProperty("updateTime") final Long updateTime,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("description") final String description,
                        @JsonProperty("namespaceURI") final String namespaceURI,
                        @JsonProperty("systemId") final String systemId,
                        @JsonProperty("data") final String data,
                        @JsonProperty("deprecated") final boolean deprecated,
                        @JsonProperty("schemaGroup") final String schemaGroup) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.namespaceURI = namespaceURI;
        this.systemId = systemId;
        this.data = data;
        this.deprecated = deprecated;
        this.schemaGroup = schemaGroup;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public void setNamespaceURI(final String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(final String systemId) {
        this.systemId = systemId;
    }

    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(final boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getSchemaGroup() {
        return schemaGroup;
    }

    public void setSchemaGroup(final String schemaGroup) {
        this.schemaGroup = schemaGroup;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final XmlSchemaDoc that = (XmlSchemaDoc) o;
        return deprecated == that.deprecated &&
                Objects.equals(description, that.description) &&
                Objects.equals(namespaceURI, that.namespaceURI) &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(data, that.data) &&
                Objects.equals(schemaGroup, that.schemaGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, namespaceURI, systemId, data, deprecated, schemaGroup);
    }
}
