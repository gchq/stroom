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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.xml.converter.ParserFactory;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.util.xml.SAXParserFactoryFactory;

import jakarta.inject.Inject;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.Reader;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

public class DS3ParserFactory implements ParserFactory {

    public static final String SCHEMA_NAME = "data-splitter-v3.0";
    public static final String NAMESPACE_URI = "data-splitter:3";
    public static final String SYSTEM_ID = "file://data-splitter-v3.0.xsd";
    public static final String SCHEMA_GROUP = "DATA_SPLITTER";

    private static final SAXParserFactory PARSER_FACTORY;
    private static long comp;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    private final SchemaFilter schemaFilter;
    private final RootFactory factory;

    @Inject
    DS3ParserFactory(final SchemaFilter schemaFilter) {
        this.schemaFilter = schemaFilter;
        this.factory = new RootFactory();
    }

    public void configure(final Reader inputStream, final ErrorHandler errorHandler) {
        try {
            final long time = System.currentTimeMillis();

            final ConfigFilter filter = new ConfigFilter(factory);

            final FindXMLSchemaCriteria schemaConstraint = new FindXMLSchemaCriteria();
            schemaConstraint.setNamespaceURI(NAMESPACE_URI);

            schemaFilter.setErrorHandler(errorHandler);
            schemaFilter.setTarget(filter);
            schemaFilter.setSchemaConstraint(schemaConstraint);
            schemaFilter.setUseOriginalLocator(true);

            final XMLReader xmlReader = PARSER_FACTORY.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(schemaFilter);
            xmlReader.setErrorHandler(errorHandler);

            // We need to start and end processing so that the schema is cached
            // correctly.
            schemaFilter.startProcessing();
            xmlReader.parse(new InputSource(inputStream));
            schemaFilter.endProcessing();

            // Try and compile the factory to link required nodes together.
            factory.compile();

            comp += (System.currentTimeMillis() - time);

        } catch (final ParserConfigurationException | IOException | SAXException e) {
            throw ProcessException.wrap(e);
        }
    }

    public long getComp() {
        final long old = comp;
        comp = 0;
        return old;
    }

    @Override
    public XMLReader getParser() {
        return new DS3Parser(factory.newInstance(new VarMap()), RootFactory.MIN_BUFFER_SIZE, factory.getBufferSize());
    }
}
