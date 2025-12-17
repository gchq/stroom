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

package stroom.xmlschema.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.HasData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "This Document defines an {{< glossary \"XML Schema\" >}} that can be used within Stroom for validation " +
        "of XML documents.\n" +
        "The XML Schema Document content is the XMLSchema text.\n" +
        "This Document also defines the following:\n" +
        "* Namespace URI - The XML namespace of the XMLSchema and the XML document that the schema " +
        "will validate.\n" +
        "* System Id - An ID (that is unique in Stroom) that can be used in the `xsi:schemaLocation` " +
        "attribute, e.g. `xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.4.2.xsd\"`.\n" +
        "* Schema Group - A name to group multiple versions of the same schema.\n" +
        "  The SchemaFilter can be configured to only use schemas matching a configured group.\n" +
        "\n" +
        "The XML Schema Document also provides a handy interactive viewer for viewing and navigating the " +
        "XMLSchema in a graphical representation.\n" +
        "\n" +
        "This Document is used by the {{< pipe-elm \"SchemaFilter\" >}} pipeline element."
)
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
        "namespaceURI",
        "data",
        "systemId",
        "deprecated",
        "schemaGroup"})
@JsonInclude(Include.NON_NULL)
public class XmlSchemaDoc extends AbstractDoc implements HasData {

    public static final String TYPE = "XMLSchema";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.XML_SCHEMA_DOCUMENT_TYPE;

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

    @JsonCreator
    public XmlSchemaDoc(@JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("version") final String version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("description") final String description,
                        @JsonProperty("namespaceURI") final String namespaceURI,
                        @JsonProperty("systemId") final String systemId,
                        @JsonProperty("data") final String data,
                        @JsonProperty("deprecated") final boolean deprecated,
                        @JsonProperty("schemaGroup") final String schemaGroup) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.namespaceURI = namespaceURI;
        this.systemId = systemId;
        this.data = data;
        this.deprecated = deprecated;
        this.schemaGroup = schemaGroup;
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<XmlSchemaDoc, XmlSchemaDoc.Builder> {

        private String description;
        private String namespaceURI;
        private String systemId;
        private String data;
        private boolean deprecated;
        private String schemaGroup;

        private Builder() {
        }

        private Builder(final XmlSchemaDoc xmlSchemaDoc) {
            super(xmlSchemaDoc);
            this.description = xmlSchemaDoc.description;
            this.namespaceURI = xmlSchemaDoc.namespaceURI;
            this.systemId = xmlSchemaDoc.systemId;
            this.data = xmlSchemaDoc.data;
            this.deprecated = xmlSchemaDoc.deprecated;
            this.schemaGroup = xmlSchemaDoc.schemaGroup;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder namespaceURI(final String namespaceURI) {
            this.namespaceURI = namespaceURI;
            return self();
        }

        public Builder systemId(final String systemId) {
            this.systemId = systemId;
            return self();
        }

        public Builder data(final String data) {
            this.data = data;
            return self();
        }

        public Builder deprecated(final boolean deprecated) {
            this.deprecated = deprecated;
            return self();
        }

        public Builder schemaGroup(final String schemaGroup) {
            this.schemaGroup = schemaGroup;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public XmlSchemaDoc build() {
            return new XmlSchemaDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    namespaceURI,
                    systemId,
                    data,
                    deprecated,
                    schemaGroup);
        }
    }
}
