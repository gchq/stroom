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

package stroom.pipeline.parser;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.SupportsCodeInjection;
import stroom.pipeline.cache.DSChooser;
import stroom.pipeline.cache.ParserFactoryPool;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.StoredParserFactory;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.DocFinder;
import stroom.pipeline.reader.BOMRemovalInputStream;
import stroom.pipeline.reader.InvalidXmlCharFilter;
import stroom.pipeline.reader.Xml10Chars;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xml.converter.ParserFactory;
import stroom.pipeline.xml.converter.json.JSONParserFactory;
import stroom.pipeline.xml.converter.xmlfragment.XMLFragmentParser;
import stroom.svg.shared.SvgImage;
import stroom.util.io.PathCreator;
import stroom.util.io.StreamUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.xml.SAXParserFactoryFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

@ConfigurableElement(
        type = PipelineElementType.TYPE_COMBINED_PARSER,
        category = Category.PARSER,
        description = """
                The original general-purpose reader/parser that covers all source data types but provides less \
                flexibility than the source format-specific parsers such as dsParser.
                It effectively combines a BOMRemovalFilterInput, an InvalidCharFilterReader and Parser (based on \
                the `type` property.

                {{% warning %}}
                It is strongly recommended to instead use a combination of Readers and one of the type \
                specific Parsers.
                This will make the intent of the pipeline much clearer and allow for much greater control.
                {{% /warning %}}
                """,
        roles = {
                PipelineElementType.ROLE_PARSER,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.ROLE_HAS_CODE},
        icon = SvgImage.PIPELINE_TEXT)
public class CombinedParser extends AbstractParser implements SupportsCodeInjection {

    public static final String DEFAULT_NAME = "combinedParser";
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    private final ParserFactoryPool parserFactoryPool;
    private final TextConverterStore textConverterStore;
    private final Provider<FeedHolder> feedHolder;
    private final Provider<PipelineHolder> pipelineHolder;
    private final DocFinder<TextConverterDoc> docFinder;
    private final Provider<LocationHolder> locationHolderProvider;

    private String type;
    private boolean fixInvalidChars = false;
    private String injectedCode;
    private boolean usePool = true;
    private DocRef textConverterRef;
    private String namePattern;
    private boolean suppressDocumentNotFoundWarnings;
    private PoolItem<StoredParserFactory> poolItem;

    @Inject
    public CombinedParser(final ErrorReceiverProxy errorReceiverProxy,
                          final LocationFactoryProxy locationFactory,
                          final ParserFactoryPool parserFactoryPool,
                          final TextConverterStore textConverterStore,
                          final PathCreator pathCreator,
                          final Provider<FeedHolder> feedHolder,
                          final Provider<PipelineHolder> pipelineHolder,
                          final Provider<LocationHolder> locationHolderProvider,
                          final DocRefInfoService docRefInfoService) {
        super(errorReceiverProxy, locationFactory);
        this.parserFactoryPool = parserFactoryPool;
        this.textConverterStore = textConverterStore;
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.locationHolderProvider = locationHolderProvider;

        this.docFinder = new DocFinder<>(
                TextConverterDoc.TYPE,
                pathCreator,
                textConverterStore,
                docRefInfoService);
    }

    @Override
    protected XMLReader createReader() throws SAXException {
        final XMLReader xmlReader = switch (getMode()) {
            case XML -> createXMLReader();
            case XML_FRAGMENT, DATA_SPLITTER -> createTextConverter();
            case JSON -> createJSONReader();
            case UNKNOWN -> throw ProcessException.create("Unknown parser type '" + type + "'");
            default -> throw ProcessException.create("Unexpected combined parser mode: " + getMode());
        };

        return xmlReader;
    }

    public Mode getMode() {
        final Mode mode;
        if (type != null && type.trim().length() > 0) {
            // TODO : Create a parser type registry that the UI selects from to
            // reduce the danger of incorrect names.
            if (type.equalsIgnoreCase("XML")
                || type.equalsIgnoreCase(TextConverterDoc.TextConverterType.NONE.getDisplayValue())) {
                mode = Mode.XML;
            } else if (type.equalsIgnoreCase("JSON")) {
                mode = Mode.JSON;
            } else if (type.equalsIgnoreCase(TextConverterDoc.TextConverterType.DATA_SPLITTER.getDisplayValue())) {
                mode = Mode.DATA_SPLITTER;
            } else if (type.equalsIgnoreCase(TextConverterDoc.TextConverterType.XML_FRAGMENT.getDisplayValue())) {
                mode = Mode.XML_FRAGMENT;
            } else {
                mode = Mode.UNKNOWN;
            }
        } else {
            // To support legacy usage that did not provide a value for parser
            // type we need to make choice based on the presence of an assigned
            // text converter.
            if (textConverterRef == null) {
                // Make an XML reader that produces SAX events.
                mode = Mode.XML;
            } else {
                // TODO: We need to use the cached TextConverter service ideally but
                //  before we do it needs to be aware cluster wide when TextConverter has
                //  been updated.
                final TextConverterDoc textConverterDoc = loadTextConverterDoc();
                mode = switch (textConverterDoc.getConverterType()) {
                    case XML_FRAGMENT -> Mode.XML_FRAGMENT;
                    case DATA_SPLITTER -> Mode.DATA_SPLITTER;
                    default -> textConverterDoc.getData().contains(DSChooser.DATA_SPLITTER_2_ELEMENT)
                            ? Mode.DATA_SPLITTER
                            : Mode.XML_FRAGMENT;
                };
            }
        }
        return mode;
    }


    private XMLReader createXMLReader() throws SAXException {
        final SAXParser parser;
        try {
            parser = PARSER_FACTORY.newSAXParser();
        } catch (final ParserConfigurationException e) {
            throw ProcessException.wrap(e);
        }
        return parser.getXMLReader();
    }

    private XMLReader createJSONReader() {
        return new JSONParserFactory().getParser();
    }

    private XMLReader createTextConverter() throws SAXException {
        // Load the latest TextConverter to get round the issue of the pipeline
        // being cached and therefore holding onto stale TextConverter.

        // TODO: We need to use the cached TextConverter service ideally but
        //  before we do it needs to be aware cluster wide when TextConverter has
        //  been updated.
        final TextConverterDoc tc = loadTextConverterDoc();

        // If we are in stepping mode and have made code changes then we want to
        // add them to the newly loaded text converter.
        if (injectedCode != null) {
            tc.setData(injectedCode);
            usePool = false;
        }

        /// Get a text converter generated parser from the pool.
        poolItem = parserFactoryPool.borrowObject(tc, usePool);
        final StoredParserFactory storedParserFactory = poolItem.getValue();
        final StoredErrorReceiver storedErrorReceiver = storedParserFactory.getErrorReceiver();
        final ParserFactory parserFactory = storedParserFactory.getParserFactory();

        final XMLReader parser;
        if (storedErrorReceiver.getTotalErrors() == 0 && parserFactory != null) {
            parser = parserFactory.getParser();

            // Fragment xml needs to be handled differently as the parser sees the
            // xml with the wrapper around it but the source does not include it.
            if (parser instanceof XMLFragmentParser) {
                locationHolderProvider.get().setFragmentXml(true);
            }
        } else {
            storedErrorReceiver.replay(new ErrorReceiverIdDecorator(getElementId(), getErrorReceiverProxy()));
            parser = null;
        }
        return parser;
    }

    @Override
    protected InputSource getInputSource(final InputSource inputSource) throws IOException {
        // Set the character encoding to use.
        final String charsetName = NullSafe.nonBlankStringElse(
                inputSource.getEncoding(), StreamUtil.DEFAULT_CHARSET_NAME);

        InputSource internalInputSource = inputSource;
        if (inputSource.getByteStream() != null) {
            // Put the BOM removal input stream in place so that any BOM
            // found is removed.
            final BOMRemovalInputStream bris = new BOMRemovalInputStream(inputSource.getByteStream(), charsetName);

            Reader inputStreamReader = new InputStreamReader(bris, charsetName);
            if (fixInvalidChars) {
                inputStreamReader = InvalidXmlCharFilter.createRemoveCharsFilter(inputStreamReader, new Xml10Chars());
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
        } catch (final RuntimeException e) {
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

    @PipelineProperty(
            description = "The parser type, e.g. 'JSON', 'XML', 'Data Splitter'.",
            displayPriority = 1)
    public void setType(final String type) {
        this.type = type;
    }

    @PipelineProperty(
            description = "The text converter configuration that should be used to parse the input data.",
            displayPriority = 2)
    @PipelinePropertyDocRef(types = TextConverterDoc.TYPE)
    public void setTextConverter(final DocRef textConverterRef) {
        this.textConverterRef = textConverterRef;
    }

    @PipelineProperty(
            description = "A name pattern to load a text converter dynamically.",
            displayPriority = 3)
    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    @PipelineProperty(
            description = "If the text converter cannot be found to match the name pattern suppress warnings.",
            defaultValue = "false",
            displayPriority = 4)
    public void setSuppressDocumentNotFoundWarnings(final boolean suppressDocumentNotFoundWarnings) {
        this.suppressDocumentNotFoundWarnings = suppressDocumentNotFoundWarnings;
    }

    @PipelineProperty(
            description = "Fix invalid XML characters from the input stream.",
            defaultValue = "false",
            displayPriority = 5)
    public void setFixInvalidChars(final boolean fixInvalidChars) {
        this.fixInvalidChars = fixInvalidChars;
    }

    public TextConverterDoc loadTextConverterDoc() {
        final DocRef docRef = findDoc(
                getFeedName(),
                getPipelineName(),
                message ->
                        getErrorReceiverProxy().log(
                                Severity.WARNING,
                                null,
                                getElementId(),
                                message,
                                null));
        if (docRef == null) {
            throw ProcessException.create(
                    "No text converter is configured or can be found to match the provided name pattern");
        } else {
            final TextConverterDoc tc = textConverterStore.readDocument(docRef);
            if (tc == null) {
                final String message = "Text converter \"" +
                                       docRef.getName() +
                                       "\" appears to have been deleted";
                throw ProcessException.create(message);
            }

            return tc;
        }
    }

    private String getFeedName() {
        if (feedHolder != null) {
            final FeedHolder fh = feedHolder.get();
            if (fh != null) {
                return fh.getFeedName();
            }
        }
        return null;
    }

    private String getPipelineName() {
        if (pipelineHolder != null) {
            final PipelineHolder ph = pipelineHolder.get();
            if (ph != null) {
                final DocRef pipeline = ph.getPipeline();
                if (pipeline != null) {
                    return pipeline.getName();
                }
            }
        }
        return null;
    }

    @Override
    public DocRef findDoc(final String feedName, final String pipelineName, final Consumer<String> errorConsumer) {
        return docFinder.findDoc(
                textConverterRef,
                namePattern,
                feedName,
                pipelineName,
                errorConsumer,
                suppressDocumentNotFoundWarnings);
    }


    // --------------------------------------------------------------------------------


    public enum Mode {
        UNKNOWN,
        XML,
        JSON,
        XML_FRAGMENT,
        DATA_SPLITTER
    }
}
