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
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

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

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.appendSuper(super.hashCode());
        builder.append(namespaceURI);
        builder.append(systemId);
        builder.append(schemaGroup);
        builder.append(user);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof FindXMLSchemaCriteria)) {
            return false;
        }

        final FindXMLSchemaCriteria criteria = (FindXMLSchemaCriteria) obj;

        final EqualsBuilder builder = new EqualsBuilder();
        builder.appendSuper(super.equals(obj));
        builder.append(namespaceURI, criteria.namespaceURI);
        builder.append(systemId, criteria.systemId);
        builder.append(schemaGroup, criteria.schemaGroup);
        builder.append(user, criteria.user);

        return builder.isEquals();
    }
}
