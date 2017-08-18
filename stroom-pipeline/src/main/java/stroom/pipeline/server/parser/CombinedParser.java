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

package stroom.pipeline.server.parser;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import stroom.cache.server.ParserFactoryPool;
import stroom.cache.server.StoredParserFactory;
import stroom.entity.shared.VersionedEntityDecorator;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.SupportsCodeInjection;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.reader.InvalidCharFilterReader;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pool.PoolItem;
import stroom.resource.server.BOMRemovalInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.spring.StroomScope;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.xml.converter.ParserFactory;
import stroom.xml.converter.json.JSONParserFactory;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

@Component
@Scope(value = StroomScope.TASK)
@ConfigurableElement(type = "CombinedParser", category = Category.PARSER, roles = {PipelineElementType.ROLE_PARSER,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
        PipelineElementType.ROLE_HAS_CODE}, icon = ElementIcons.TEXT)
public class CombinedParser extends AbstractParser implements SupportsCodeInjection {
    public static final String DEFAULT_NAME = "combinedParser";
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    private final ParserFactoryPool parserFactoryPool;
    private final TextConverterService textConverterService;
    private String type;
    private boolean fixInvalidChars = false;
    private String injectedCode;
    private boolean usePool = true;
    private TextConverter textConverter;
    private PoolItem<VersionedEntityDecorator<TextConverter>, StoredParserFactory> poolItem;

    @Inject
    public CombinedParser(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory,
                          final ParserFactoryPool parserFactoryPool, final TextConverterService textConverterService) {
        super(errorReceiverProxy, locationFactory);
        this.parserFactoryPool = parserFactoryPool;
        this.textConverterService = textConverterService;
    }

    @Override
    protected XMLReader createReader() throws SAXException {
        XMLReader xmlReader = null;

        if (type != null && type.trim().length() > 0) {
            // TODO : Create a parser type registry that the UI selects from to
            // reduce the danger of incorrect names.
            if (type.equalsIgnoreCase("XML")
                    || type.equalsIgnoreCase(TextConverter.TextConverterType.NONE.getDisplayValue())) {
                xmlReader = createXMLReader();
            } else if (type.equalsIgnoreCase("JSON")) {
                xmlReader = createJSONReader();
            } else if (type.equalsIgnoreCase(TextConverter.TextConverterType.DATA_SPLITTER.getDisplayValue())) {
                xmlReader = createTextConverter();
            } else if (type.equalsIgnoreCase(TextConverter.TextConverterType.XML_FRAGMENT.getDisplayValue())) {
                xmlReader = createTextConverter();
            } else {
                throw new ProcessException("Unknown parser type '" + type + "'");
            }
        } else {
            // To support legacy usage that did not provide a value for parser
            // type we need to make choice based on the presence of an assigned
            // text converter.
            if (textConverter == null) {
                // Make an XML reader that produces SAX events.
                xmlReader = createXMLReader();
            } else {
                xmlReader = createTextConverter();
            }
        }

        return xmlReader;
    }

    private XMLReader createXMLReader() throws SAXException {
        SAXParser parser = null;
        try {
            parser = PARSER_FACTORY.newSAXParser();
        } catch (final ParserConfigurationException e) {
            throw ProcessException.wrap(e);
        }
        return parser.getXMLReader();
    }

    private XMLReader createJSONReader() throws SAXException {
        return new JSONParserFactory().getParser();
    }

    private XMLReader createTextConverter() throws SAXException {
        if (textConverter == null) {
            throw new ProcessException(
                    "No text converter has been assigned to the parser but parsers of type '" + type + "' require one");
        }

        // Load the latest TextConverter to get round the issue of the pipeline
        // being cached and therefore holding onto stale TextConverter.

        // TODO: We need to use the cached TextConverter service ideally but
        // before we do it needs to be aware cluster wide when TextConverter has
        // been updated.
        final TextConverter tc = textConverterService.loadByUuid(textConverter.getUuid());
        if (tc == null) {
            throw new ProcessException(
                    "TextConverter \"" + textConverter.getName() + "\" appears to have been deleted");
        }

        // If we are in stepping mode and have made code changes then we want to
        // add them to the newly loaded text converter.
        if (injectedCode != null) {
            tc.setData(injectedCode);
            usePool = false;
        }

        /// Get a text converter generated parser from the pool.
        poolItem = parserFactoryPool.borrowObject(new VersionedEntityDecorator<>(tc), usePool);
        final StoredParserFactory storedParserFactory = poolItem.getValue();
        final StoredErrorReceiver storedErrorReceiver = storedParserFactory.getErrorReceiver();
        final ParserFactory parserFactory = storedParserFactory.getParserFactory();

        if (storedErrorReceiver.getTotalErrors() == 0 && parserFactory != null) {
            return parserFactory.getParser();
        } else {
            storedErrorReceiver.replay(new ErrorReceiverIdDecorator(getElementId(), getErrorReceiverProxy()));
        }

        return null;
    }

    @Override
    protected InputSource getInputSource(final InputSource inputSource) throws IOException {
        // Set the character encoding to use.
        String charsetName = StreamUtil.DEFAULT_CHARSET_NAME;
        if (inputSource.getEncoding() != null && inputSource.getEncoding().trim().length() > 0) {
            charsetName = inputSource.getEncoding();
        }

        InputSource internalInputSource = inputSource;
        if (inputSource.getByteStream() != null) {
            // Put the BOM removal input stream in place so that any BOM
            // found is removed.
            final BOMRemovalInputStream bris = new BOMRemovalInputStream(inputSource.getByteStream(), charsetName);

            Reader inputStreamReader = new InputStreamReader(bris, charsetName);
            if (fixInvalidChars) {
                inputStreamReader = new InvalidCharFilterReader(inputStreamReader);
            }

            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            internalInputSource = new InputSource(bufferedReader);
            internalInputSource.setEncoding(charsetName);
        }

        return internalInputSource;
    }

    @Override
    public void endProcessing() {
        try {
            super.endProcessing();
        } catch (final LoggedException e) {
            throw e;
        } catch (final Throwable e) {
            throw ProcessException.wrap(e);
        } finally {
            // Return the parser factory to the pool if we have used one.
            if (poolItem != null && parserFactoryPool != null) {
                parserFactoryPool.returnObject(poolItem, usePool);
            }
        }
    }

    @Override
    public void setInjectedCode(final String injectedCode) {
        this.injectedCode = injectedCode;
    }

    @PipelineProperty(description = "The parser type, e.g. 'JSON', 'XML', 'Data Splitter'.")
    public void setType(final String type) {
        this.type = type;
    }

    @PipelineProperty(description = "The text converter configuration that should be used to parse the input data.")
    public void setTextConverter(final TextConverter textConverter) {
        this.textConverter = textConverter;
    }

    @PipelineProperty(description = "Fix invalid XML characters from the input stream.", defaultValue = "false")
    public void setFixInvalidChars(final boolean fixInvalidChars) {
        this.fixInvalidChars = fixInvalidChars;
    }
}
