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

import stroom.entity.shared.FindDocumentEntityCriteria;

import java.util.Objects;

public class FindXMLSchemaCriteria extends FindDocumentEntityCriteria {
    private static final long serialVersionUID = 8723582276742882390L;

    private String namespaceURI;
    private String systemId;
    private String schemaGroup;
    private String user;

    public FindXMLSchemaCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindXMLSchemaCriteria(final String name) {
        super(name);
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

    public String getSchemaGroup() {
        return schemaGroup;
    }

    public void setSchemaGroup(final String schemaGroup) {
        this.schemaGroup = schemaGroup;
    }

    public String getUser() {
        return user;
    }

    public void setUser(final String user) {
        this.user = user;
    }

    public boolean matches(final XmlSchemaDoc doc) {
        if (getName() != null && !getName().isMatch(doc.getName())) {
            return false;
        }
        if (namespaceURI != null && !namespaceURI.equals(doc.getNamespaceURI())) {
            return false;
        }
        if (systemId != null && !systemId.equals(doc.getSystemId())) {
            return false;
        }
        if (schemaGroup != null && !schemaGroup.equals(doc.getSchemaGroup())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final FindXMLSchemaCriteria that = (FindXMLSchemaCriteria) o;
        return Objects.equals(namespaceURI, that.namespaceURI) &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(schemaGroup, that.schemaGroup) &&
                Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespaceURI, systemId, schemaGroup, user);
    }
}
