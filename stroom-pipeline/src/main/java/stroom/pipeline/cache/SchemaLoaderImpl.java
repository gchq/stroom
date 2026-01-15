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

package stroom.pipeline.cache;

import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.filter.LSResourceResolverImpl;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

public class SchemaLoaderImpl implements SchemaLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SchemaLoaderImpl.class);

    private static final ElementId ELEMENT_ID = new ElementId(SchemaLoader.class.getSimpleName());

    private final XmlSchemaCache xmlSchemaCache;

    @Inject
    public SchemaLoaderImpl(final XmlSchemaCache xmlSchemaCache) {
        this.xmlSchemaCache = xmlSchemaCache;
    }

    @Override
    public StoredSchema load(final String schemaLanguage,
                             final String data,
                             final FindXMLSchemaCriteria findXMLSchemaCriteria) {
        LOGGER.debug(() -> "Creating schema: " + data);
        final StoredErrorReceiver errorReceiver = new StoredErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final ErrorHandler errorHandler = new ErrorHandlerAdaptor(
                ELEMENT_ID,
                locationFactory,
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
            LOGGER.debug(e::getMessage, e);
            errorReceiver.log(Severity.FATAL_ERROR,
                    null,
                    ELEMENT_ID,
                    e.getMessage(),
                    e);
        } finally {
            try {
                inputStream.close();
            } catch (final IOException e) {
                LOGGER.debug(e::getMessage, e);
                errorReceiver.log(Severity.FATAL_ERROR,
                        null,
                        ELEMENT_ID,
                        e.getMessage(),
                        e);
            }
        }

        return new StoredSchema(schema, errorReceiver);
    }
}
