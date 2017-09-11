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

package stroom.xml.converter;

import java.io.File;

import stroom.cache.server.MockSchemaPool;
import stroom.cache.server.SchemaLoaderImpl;
import stroom.cache.server.SchemaPool;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.filter.SchemaFilter;
import stroom.pipeline.state.PipelineContext;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.xml.converter.ds3.DS3ParserFactory;
import stroom.xmlschema.server.MockXMLSchemaService;
import stroom.xmlschema.server.XMLSchemaCache;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

public class SchemaFilterFactory {
    private final XMLSchemaService xmlSchemaService = new MockXMLSchemaService();
    private final XMLSchemaCache xmlSchemaCache = new XMLSchemaCache(xmlSchemaService);
    private final SchemaLoaderImpl schemaLoader = new SchemaLoaderImpl(xmlSchemaCache);

    public SchemaFilterFactory() {
        // loadXMLSchema(DataSplitterParserFactory.SCHEMA_GROUP,
        // DataSplitterParserFactory.SCHEMA_NAME,
        // DataSplitterParserFactory.NAMESPACE_URI,
        // DataSplitterParserFactory.SYSTEM_ID,
        // "data-splitter/v1.4/data-splitter-v1.4.xsd");
        // loadXMLSchema(DS2ParserFactory.SCHEMA_GROUP,
        // DS2ParserFactory.SCHEMA_NAME, DS2ParserFactory.NAMESPACE_URI,
        // DS2ParserFactory.SYSTEM_ID,
        // "data-splitter/v2.0/data-splitter-v2.0.xsd");
        loadXMLSchema(DS3ParserFactory.SCHEMA_GROUP, DS3ParserFactory.SCHEMA_NAME, DS3ParserFactory.NAMESPACE_URI,
                DS3ParserFactory.SYSTEM_ID, "data-splitter/v3.0/data-splitter-v3.0.xsd");
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

    public void loadXMLSchema(final String schemaGroup, final String schemaName, final String namespaceURI,
            final String systemId, final String fileName) {
        final File dir = FileSystemTestUtil.getConfigXSDDir();

        final File file = new File(dir, fileName);

        final XMLSchema xmlSchema = new XMLSchema();
        xmlSchema.setSchemaGroup(schemaGroup);
        xmlSchema.setName(schemaName);
        xmlSchema.setNamespaceURI(namespaceURI);
        xmlSchema.setSystemId(systemId);
        xmlSchema.setData(StreamUtil.fileToString(file));

        final FindXMLSchemaCriteria criteria = new FindXMLSchemaCriteria();
        criteria.getName().setString(schemaName);

        if (xmlSchemaService.find(criteria).size() == 0) {
            xmlSchemaService.save(xmlSchema);
        }
    }
}
