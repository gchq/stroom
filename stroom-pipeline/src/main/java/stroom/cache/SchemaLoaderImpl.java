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

package stroom.cache.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.filter.LSResourceResolverImpl;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;
import stroom.xmlschema.server.XMLSchemaCache;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;

import javax.inject.Inject;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;

public class SchemaLoaderImpl implements SchemaLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaLoaderImpl.class);

    private final XMLSchemaCache xmlSchemaCache;

    @Inject
    public SchemaLoaderImpl(final XMLSchemaCache xmlSchemaCache) {
        this.xmlSchemaCache = xmlSchemaCache;
    }

    @Override
    public StoredSchema load(final String schemaLanguage,
                             final String data,
                             final FindXMLSchemaCriteria findXMLSchemaCriteria) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating schema: " + data);
        }

        final StoredErrorReceiver errorReceiver = new StoredErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final ErrorHandler errorHandler = new ErrorHandlerAdaptor(getClass().getSimpleName(), locationFactory,
                errorReceiver);
        Schema schema = null;

        // Turn the string into a stream so we can load the schema.
        final InputStream inputStream = StreamUtil.stringToStream(data);

        try {
            final SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);
            if (schemaFactory == null) {
                throw new RuntimeException("Unable to create SchemaFactory for: '" + schemaLanguage + "'");
            }

            schemaFactory.setErrorHandler(errorHandler);
            schemaFactory.setResourceResolver(new LSResourceResolverImpl(xmlSchemaCache, findXMLSchemaCriteria));

            schema = schemaFactory.newSchema(new StreamSource(inputStream));

        } catch (final SAXException e) {
            LOGGER.debug(e.getMessage(), e);
            errorReceiver.log(Severity.FATAL_ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
        } finally {
            try {
                inputStream.close();
            } catch (final IOException e) {
                LOGGER.debug(e.getMessage(), e);
                errorReceiver.log(Severity.FATAL_ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        return new StoredSchema(schema, errorReceiver);
    }
}
