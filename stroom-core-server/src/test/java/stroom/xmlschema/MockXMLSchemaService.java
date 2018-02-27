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
 *
 */

package stroom.xmlschema;

import stroom.entity.MockDocumentEntityService;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;

import javax.inject.Inject;

public class MockXMLSchemaService extends MockDocumentEntityService<XMLSchema, FindXMLSchemaCriteria>
        implements XMLSchemaService, ExplorerActionHandler, ImportExportActionHandler {

    public MockXMLSchemaService() {
    }

    @Inject
    MockXMLSchemaService(final ImportExportHelper importExportHelper) {
        super(importExportHelper);
    }

    @Override
    public boolean isMatch(final FindXMLSchemaCriteria criteria, final XMLSchema xmlSchema) {
        if (!super.isMatch(criteria, xmlSchema)) {
            return false;
        }

        if (criteria.getSchemaGroup() != null && !criteria.getSchemaGroup().equals(xmlSchema.getSchemaGroup())) {
            return false;
        }

        if (criteria.getSystemId() != null && !criteria.getSystemId().equals(xmlSchema.getSystemId())) {
            return false;
        }

        return !(criteria.getNamespaceURI() != null && !criteria.getNamespaceURI().equals(xmlSchema.getNamespaceURI()));
    }

    @Override
    public Class<XMLSchema> getEntityClass() {
        return XMLSchema.class;
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(13, XMLSchema.ENTITY_TYPE, "XML Schema");
    }
}
