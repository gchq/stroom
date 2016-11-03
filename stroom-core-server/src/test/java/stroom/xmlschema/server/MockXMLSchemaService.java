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

package stroom.xmlschema.server;

import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.importexport.server.ImportExportSerializer.ImportMode;
import stroom.importexport.server.ImportExportSerializerImpl;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.spring.StroomSpringProfiles;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.HashMap;

@Profile(StroomSpringProfiles.TEST)
@Component
public class MockXMLSchemaService extends MockDocumentEntityService<XMLSchema, FindXMLSchemaCriteria>
        implements XMLSchemaService {
    private final File xsdDir;
    @Resource
    private FolderService folderService;
    @Resource
    private ImportExportSerializerImpl importExportSerializer;
    private boolean loaded;

    public MockXMLSchemaService() {
        xsdDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config/XML Schemas");
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
    public BaseResultList<XMLSchema> find(final FindXMLSchemaCriteria criteria) {
        if (!loaded && xsdDir != null && folderService != null) {
            loaded = true;
            importExportSerializer.performImport(xsdDir, Folder.class, Folder.ENTITY_TYPE, new HashMap<>(),
                    ImportMode.IGNORE_CONFIRMATION);
            importExportSerializer.performImport(xsdDir, XMLSchema.class, XMLSchema.ENTITY_TYPE, new HashMap<>(),
                    ImportMode.IGNORE_CONFIRMATION);
        }

        return super.find(criteria);
    }

    @Override
    public void clear() {
        super.clear();
        loaded = false;
    }

    @Override
    public Class<XMLSchema> getEntityClass() {
        return XMLSchema.class;
    }
}
