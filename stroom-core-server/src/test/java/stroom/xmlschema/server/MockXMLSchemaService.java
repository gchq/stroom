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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.FolderService;
import stroom.importexport.server.EntityPathResolver;
import stroom.importexport.server.ImportExportSerializerImpl;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.spring.StroomSpringProfiles;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;

@Profile(StroomSpringProfiles.TEST)
@Component
public class MockXMLSchemaService extends MockDocumentEntityService<XMLSchema, FindXMLSchemaCriteria>
        implements XMLSchemaService {
    private final Path xsdDir;
    private final FolderService folderService;
    private final ImportExportSerializerImpl importExportSerializer;
    private boolean loaded;

    public MockXMLSchemaService() {
        this.folderService = null;
        this.importExportSerializer = null;
        xsdDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config/XML Schemas").toPath();
    }

    @Inject
    public MockXMLSchemaService(final GenericEntityService genericEntityService, final EntityPathResolver entityPathResolver, final FolderService folderService, final ImportExportSerializerImpl importExportSerializer) {
        super(genericEntityService, entityPathResolver);
        this.folderService = folderService;
        this.importExportSerializer = importExportSerializer;
        xsdDir = new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), "samples/config/XML Schemas").toPath();
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
//        if (!loaded && xsdDir != null && folderService != null) {
//            loaded = true;
//            importExportSerializer.performImport(xsdDir, Folder.ENTITY_TYPE, new HashMap<>(),
//                    ImportMode.IGNORE_CONFIRMATION);
//            importExportSerializer.performImport(xsdDir, XMLSchema.ENTITY_TYPE, new HashMap<>(),
//                    ImportMode.IGNORE_CONFIRMATION);
//        }

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
