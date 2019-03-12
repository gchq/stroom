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

package stroom.pipeline.xmlschema.migration;

import stroom.importexport.shared.ExternalFile;
import stroom.importexport.migration.DocumentEntity;

import javax.persistence.Transient;

/** Used for legacy migration **/
@Deprecated
public class OldXMLSchema extends DocumentEntity {
    private static final String ENTITY_TYPE = "XMLSchema";

    private String description;
    private String namespaceURI;
    private String systemId;
    private String data;
    private boolean deprecated;
    private String schemaGroup;

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

    @ExternalFile("xsd")
    public String getData() {
        return data;
    }

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

    @Transient
    public final String getType() {
        return ENTITY_TYPE;
    }
}
