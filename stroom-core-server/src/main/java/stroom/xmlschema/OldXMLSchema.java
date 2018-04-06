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

package stroom.xmlschema;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "XML_SCHEMA")
public class OldXMLSchema extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.XML + SEP + SQLNameConstants.SCHEMA;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String SYSTEM_ID = SQLNameConstants.SYSTEM + ID_SUFFIX;
    public static final String NAMESPACE = SQLNameConstants.NAMESPACE;
    public static final String DEPRECATED = SQLNameConstants.DEPRECATED;
    public static final String DATA = SQLNameConstants.DATA;
    public static final String SCHEMA_GROUP = SQLNameConstants.SCHEMA + SEP + SQLNameConstants.GROUP;
    public static final String ENTITY_TYPE = "XMLSchema";

    private static final long serialVersionUID = 4519634323788508083L;

    private String description;
    private String namespaceURI;
    private String systemId;
    private String data;
    private boolean deprecated;
    private String schemaGroup;

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Column(name = NAMESPACE)
    public String getNamespaceURI() {
        return namespaceURI;
    }

    public void setNamespaceURI(final String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }

    @Column(name = SYSTEM_ID)
    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(final String systemId) {
        this.systemId = systemId;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile("xsd")
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Column(name = DEPRECATED, nullable = false)
    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(final boolean deprecated) {
        this.deprecated = deprecated;
    }

    @Column(name = SCHEMA_GROUP)
    public String getSchemaGroup() {
        return schemaGroup;
    }

    public void setSchemaGroup(final String schemaGroup) {
        this.schemaGroup = schemaGroup;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
