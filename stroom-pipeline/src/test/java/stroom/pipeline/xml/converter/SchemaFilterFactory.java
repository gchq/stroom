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

package stroom.pipeline.xml.converter;

import stroom.content.ContentPack;
import stroom.content.ContentPacks;
import stroom.docref.DocRef;
import stroom.docrefinfo.mock.MockDocRefInfoService;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.cache.MockSchemaPool;
import stroom.pipeline.cache.SchemaLoaderImpl;
import stroom.pipeline.cache.SchemaPool;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.xml.converter.ds3.DS3ParserFactory;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.pipeline.xmlschema.XmlSchemaSerialiser;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.pipeline.xmlschema.XmlSchemaStoreImpl;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.ContentPackZipDownloader;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.StreamUtil;
import stroom.xmlschema.shared.XmlSchemaDoc;

import java.nio.file.Path;

public class SchemaFilterFactory {

    private final SecurityContext securityContext = new MockSecurityContext();
    private final Persistence persistence = new MemoryPersistence();
    private final XmlSchemaSerialiser serialiser = new XmlSchemaSerialiser(new Serialiser2FactoryImpl());
    private final XmlSchemaStore xmlSchemaStore = new XmlSchemaStoreImpl(
            new StoreFactoryImpl(
                    persistence,
                    null,
                    securityContext,
                    MockDocRefInfoService::new),
            serialiser);
    private final XmlSchemaCache xmlSchemaCache = new XmlSchemaCache(xmlSchemaStore, securityContext);
    private final SchemaLoaderImpl schemaLoader = new SchemaLoaderImpl(xmlSchemaCache);

    public SchemaFilterFactory() {
        loadSchemas();
    }

    private void loadSchemas() {
        loadXMLSchema(
                DS3ParserFactory.SCHEMA_GROUP,
                DS3ParserFactory.SCHEMA_NAME,
                DS3ParserFactory.NAMESPACE_URI,
                DS3ParserFactory.SYSTEM_ID,
                ContentPacks.CORE_XML_SCHEMAS_PACK,
                "XML_Schemas/" +
                "data_splitter/" +
                "data_splitter_v3_0.XMLSchema.9e1e2567-ba83-4720-95c0-f882b951bd3e.xsd");
    }

    public SchemaFilter getSchemaFilter(final String namespaceURI, final ErrorReceiverProxy errorReceiverProxy) {
        final SchemaPool schemaPool = new MockSchemaPool(schemaLoader);
        final FindXMLSchemaCriteria schemaConstraint = new FindXMLSchemaCriteria();
        schemaConstraint.setNamespaceURI(namespaceURI);

        final SchemaFilter schemaFilter = new SchemaFilter(schemaPool, xmlSchemaCache, errorReceiverProxy,
                new LocationFactoryProxy(), new PipelineContext());
        schemaFilter.setSchemaConstraint(schemaConstraint);
        schemaFilter.setUseOriginalLocator(true);

        return schemaFilter;
    }

    private void loadXMLSchema(final String schemaGroup,
                               final String schemaName,
                               final String namespaceURI,
                               final String systemId,
                               final ContentPack contentPack,
                               final String fileName) {
        final Path schemaFile = getSchemaFile(contentPack, fileName);

        final DocRef docRef = xmlSchemaStore.createDocument(schemaName);
        final XmlSchemaDoc xmlSchema = xmlSchemaStore.readDocument(docRef);
        xmlSchema.setSchemaGroup(schemaGroup);
        xmlSchema.setName(schemaName);
        xmlSchema.setNamespaceURI(namespaceURI);
        xmlSchema.setSystemId(systemId);
        xmlSchema.setData(StreamUtil.fileToString(schemaFile));
        xmlSchemaStore.writeDocument(xmlSchema);
    }

    private Path getSchemaFile(final ContentPack contentPack,
                               final String fileName) {
        final Path dir = ContentPackZipDownloader.downloadContentPack(
                contentPack,
                FileSystemTestUtil.getExplodedContentPacksDir());
        return dir.resolve(contentPack.getPath()).resolve(fileName);
    }
}
