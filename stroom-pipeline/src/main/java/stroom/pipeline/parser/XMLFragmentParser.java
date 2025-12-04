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
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xml.converter.ParserFactory;
import stroom.svg.shared.SvgImage;
import stroom.util.io.PathCreator;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.util.function.Consumer;

@ConfigurableElement(
        type = PipelineElementType.TYPE_XML_FRAGMENT_PARSER,
        category = Category.PARSER,
        description = """
                A parser to convert multiple XML fragments into an XML document.
                For example the data may contain:
                ```xml
                <Event>...</Event>
                <Event>...</Event>
                ```
                i.e. with no root element, so not valid XML.
                The XMLFragmentParser will wrap the fragments with a root element as defined in the TextConverter \
                document configured with the `textConverterRef` property.""",
        roles = {
                PipelineElementType.ROLE_PARSER,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.ROLE_HAS_CODE},
        icon = SvgImage.PIPELINE_XML)
public class XMLFragmentParser extends AbstractParser implements SupportsCodeInjection {

    private final ParserFactoryPool parserFactoryPool;
    private final TextConverterStore textConverterStore;
    private final Provider<FeedHolder> feedHolder;
    private final Provider<PipelineHolder> pipelineHolder;
    private final DocFinder<TextConverterDoc> docFinder;
    private final Provider<LocationHolder> locationHolderProvider;

    private String injectedCode;
    private boolean usePool = true;
    private DocRef textConverterRef;
    private String namePattern;
    private boolean suppressDocumentNotFoundWarnings;
    private PoolItem<StoredParserFactory> poolItem;

    @Inject
    public XMLFragmentParser(final ErrorReceiverProxy errorReceiverProxy,
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
        // Load the latest TextConverter to get round the issue of the pipeline
        // being cached and therefore holding onto stale TextConverter.

        // TODO: We need to use the cached TextConverter service ideally but
        // before we do it needs to be aware cluster wide when TextConverter has
        // been updated.
        final TextConverterDoc tc = loadTextConverterDoc();
        if (!TextConverterType.XML_FRAGMENT.equals(tc.getConverterType())) {
            throw ProcessException.create("The assigned text converter is not an XML fragment.");
        }

        // If we are in stepping mode and have made code changes then we want to
        // add them to the newly loaded text
        // converter.
        if (injectedCode != null) {
            tc.setData(injectedCode);
            usePool = false;
        }

        // Get a text converter generated parser from the pool.
        poolItem = parserFactoryPool.borrowObject(tc, usePool);
        final StoredParserFactory storedParserFactory = poolItem.getValue();
        final StoredErrorReceiver storedErrorReceiver = storedParserFactory.getErrorReceiver();
        final ParserFactory parserFactory = storedParserFactory.getParserFactory();

        final XMLReader parser;
        if (storedErrorReceiver.getTotalErrors() == 0 && parserFactory != null) {
            parser = parserFactory.getParser();

            // Fragment xml needs to be handled differently as the parser sees the
            // xml with the wrapper around it but the source does not include it.
            if (parser instanceof stroom.pipeline.xml.converter.xmlfragment.XMLFragmentParser) {
                locationHolderProvider.get().setFragmentXml(true);
            }
        } else {
            storedErrorReceiver.replay(new ErrorReceiverIdDecorator(getElementId(), getErrorReceiverProxy()));
            parser = null;
        }
        return parser;
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
            description = "The XML fragment wrapper that should be used to wrap the input XML.",
            displayPriority = 1)
    @PipelinePropertyDocRef(types = TextConverterDoc.TYPE)
    public void setTextConverter(final DocRef textConverterRef) {
        this.textConverterRef = textConverterRef;
    }

    @PipelineProperty(
            description = "A name pattern to load a text converter dynamically.",
            displayPriority = 2)
    public void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    @PipelineProperty(
            description = "If the text converter cannot be found to match the name pattern suppress warnings.",
            defaultValue = "false", displayPriority = 3)
    public void setSuppressDocumentNotFoundWarnings(final boolean suppressDocumentNotFoundWarnings) {
        this.suppressDocumentNotFoundWarnings = suppressDocumentNotFoundWarnings;
    }

    public TextConverterDoc loadTextConverterDoc() {
        final DocRef docRef = findDoc(
                getFeedName(),
                getPipelineName(),
                message ->
                        getErrorReceiverProxy().log(Severity.WARNING, null, getElementId(), message, null));
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
}
