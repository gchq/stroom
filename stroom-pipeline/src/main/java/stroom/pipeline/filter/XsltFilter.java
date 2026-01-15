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

package stroom.pipeline.filter;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.SupportsCodeInjection;
import stroom.pipeline.cache.PoolItem;
import stroom.pipeline.cache.StoredXsltExecutable;
import stroom.pipeline.cache.XsltPool;
import stroom.pipeline.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.xslt.XsltStore;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.Configuration;
import net.sf.saxon.jaxp.TemplatesImpl;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XsltExecutable;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;

/**
 * An XML filter for performing inline XSLT transformation of XML.
 */
@ConfigurableElement(
        type = "XSLTFilter",
        description = """
                An element used to transform XML data from one form to another using XSLT.
                The specified XSLT can be used to transform the input XML into XML conforming to another \
                schema or into other forms such as JSON, plain text, etc.
                """,
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.ROLE_HAS_CODE},
        icon = SvgImage.PIPELINE_XSLT)
public class XsltFilter extends AbstractXMLFilter implements SupportsCodeInjection {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltFilter.class);

    private final XsltPool xsltPool;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final XsltStore xsltStore;
    private final XsltConfig xsltConfig;
    private final LocationFactoryProxy locationFactory;
    private final PipelineContext pipelineContext;
    private final Provider<FeedHolder> feedHolder;
    private final Provider<PipelineHolder> pipelineHolder;
    private final DocFinder<XsltDoc> docFinder;

    private ErrorListener errorListener;

    private DocRef xsltRef;
    private String xsltNamePattern;
    private boolean suppressXSLTNotFoundWarnings;

    /**
     * We only need a single transformer factory here as it actually doesn't do
     * much internally when creating a transformer handler.
     */
    private PoolItem<StoredXsltExecutable> poolItem;
    private XsltExecutable xsltExecutable;
    private TransformerHandler handler;
    private Locator locator;
    private boolean xsltRequired = false;
    private boolean passThrough = true;
    private String injectedCode;
    private boolean usePool = true;
    private List<PipelineReference> pipelineReferences;

    private int elementCount;
    private int maxElementCount;

    @Inject
    public XsltFilter(final XsltPool xsltPool,
                      final ErrorReceiverProxy errorReceiverProxy,
                      final XsltStore xsltStore,
                      final XsltConfig xsltConfig,
                      final LocationFactoryProxy locationFactory,
                      final PipelineContext pipelineContext,
                      final PathCreator pathCreator,
                      final Provider<FeedHolder> feedHolder,
                      final Provider<PipelineHolder> pipelineHolder,
                      final DocRefInfoService docRefInfoService) {
        this.xsltPool = xsltPool;
        this.errorReceiverProxy = errorReceiverProxy;
        this.xsltStore = xsltStore;
        this.xsltConfig = xsltConfig;
        this.locationFactory = locationFactory;
        this.pipelineContext = pipelineContext;
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;

        this.docFinder = new DocFinder<>(XsltDoc.TYPE, pathCreator, xsltStore, docRefInfoService);
    }

    @Override
    public void startProcessing() {
        try {
            errorListener = new ErrorListenerAdaptor(getElementId(), locationFactory, errorReceiverProxy);
            maxElementCount = xsltConfig.getMaxElements();

            final XsltDoc xslt = loadXsltDoc();

            // If we have found XSLT then get a template.
            if (xslt != null) {
                // If we are in stepping mode and have made code changes then we
                // want to add them to the newly loaded XSLT.

                if (injectedCode != null) {
                    xslt.setData(injectedCode);
                    usePool = false;
                }

                // If no XSLT has been provided then don't try and get compiled
                // XSLT for it.
                if (xslt.getData() != null && xslt.getData().trim().length() > 0) {
                    // Get compiled XSLT from the pool.
                    final ErrorReceiver errorReceiver = new ErrorReceiverIdDecorator(getElementId(),
                            errorReceiverProxy);
                    poolItem = xsltPool.borrowConfiguredTemplate(xslt, errorReceiver,
                            locationFactory, pipelineReferences, usePool);
                    final StoredXsltExecutable storedXsltExecutable = poolItem.getValue();
                    // Get the errors.
                    final StoredErrorReceiver storedErrors = storedXsltExecutable.getErrorReceiver();
                    // Get the XSLT executable.
                    xsltExecutable = storedXsltExecutable.getXsltExecutable();

                    if (storedErrors.getTotalErrors() > 0) {
                        // Replay any exceptions that were created when
                        // compiling the XSLT into the pipeline error handler.
                        storedErrors.replay(errorReceiver);
                    }

                    if (xsltExecutable == null) {
                        // If the XSLT has previously failed to compile they
                        // will have stored null in the pool. Throw an exception
                        // to record this.
                        final CharBuffer sb = new CharBuffer(100);
                        sb.append("There is a problem with the XSLT \"");
                        sb.append(xslt.getName());
                        sb.append("\", see log entries for details.");
                        final String msg = sb.toString();
                        throw ProcessException.create(msg);
                    }
                }
            }

            if (xsltRequired && xsltExecutable == null && !pipelineContext.isStepping()) {
                passThrough = false;
                final String msg = "XSLT is required but either no XSLT was found or there is an error in the XSLT";
                throw ProcessException.create(msg);
            }
        } catch (final RuntimeException e) {
            errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), e.getMessage(), e);
            // If we aren't stepping then throw an exception to terminate early.
            if (!pipelineContext.isStepping()) {
                throw LoggedException.wrap(e);
            }
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {
            if (poolItem != null) {
                xsltPool.returnObject(poolItem, usePool);
                poolItem = null;
            }
        } finally {
            super.endProcessing();
        }
    }

    /**
     * @param locator an object that can return the location of any SAX document
     *                event
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.filter.AbstractXMLFilter#setDocumentLocator(org.xml.sax.Locator)
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        if (this.locator == null) {
            this.locator = locator;
            super.setDocumentLocator(locator);
        }
    }

    /**
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endDocument
     * @see org.xml.sax.helpers.XMLFilterImpl#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        try {
            if (xsltExecutable != null) {
                // Make sure the executable points at the local error handler.
                final Configuration configuration = xsltExecutable.getUnderlyingCompiledStylesheet().getConfiguration();
                configuration.setErrorListener(errorListener);
//                configuration.setLineNumbering(!pipelineContext.isStepping());

                // Create a handler to receive all SAX events.
                final TemplatesImpl templates = new TemplatesImpl(xsltExecutable);
                final TransformerImpl transformer = (TransformerImpl) templates.newTransformer();
                transformer.setErrorListener(errorListener);
                configureMessageListener(transformer);

                handler = transformer.newTransformerHandler();
                handler.setResult(new SAXResult(getFilter()));
                if (locator != null) {
                    handler.setDocumentLocator(locator);
                }
                handler.startDocument();

            } else if (passThrough) {
                super.startDocument();
            }

        } catch (final RuntimeException e) {
            final Throwable throwable = unwrapException(e);

            errorReceiverProxy.log(Severity.FATAL_ERROR,
                    getLocation(throwable),
                    getElementId(),
                    throwable.toString(),
                    throwable);
            // If we aren't stepping then throw an exception to terminate early.
            if (!pipelineContext.isStepping()) {
                throw LoggedException.wrap(throwable);
            }
        }
    }

    /**
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #startDocument
     * @see stroom.pipeline.filter.AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        if (handler != null) {
            try {
                handler.endDocument();
            } catch (final Throwable e) {
                try {
                    final Throwable throwable = unwrapException(e);

                    errorReceiverProxy.log(Severity.FATAL_ERROR,
                            getLocation(throwable),
                            getElementId(),
                            throwable.toString(),
                            throwable);
                    // If we aren't stepping then throw an exception to terminate early.
                    if (!pipelineContext.isStepping()) {
                        throw LoggedException.wrap(throwable);
                    }

                } finally {
                    // We don't want the whole pipeline to terminate processing
                    // if there is a problem with the transform.
                    super.endDocument();
                }
            } finally {
                handler = null;
                elementCount = 0;
            }
        } else if (passThrough) {
            super.endDocument();
        }
    }

    private void configureMessageListener(final TransformerImpl transformer) {
        try {
            NullSafe.consume(
                    transformer,
                    TransformerImpl::getUnderlyingXsltTransformer,
                    xsltTransformer ->
                            xsltTransformer.setMessageListener(this::onXsltMessage));
        } catch (final Exception e) {
            // Just log and swallow as the message listener is not critical
            LOGGER.error("Error configuring XSLT message listener: " + e.getMessage(), e);
        }
    }

    /**
     * This is called when <pre>{@code <xsl:message>my message</xsl:message>}</pre>
     * is used in the XSLT.
     * It will log a message to the errorReceiver as ERROR.
     * If the {@code terminate} attribute is used like so:
     * <pre>{@code <xsl:message terminate="yes">my message</xsl:message>}</pre>
     * then it will log it as FATAL and throw an exception which will immediately terminate processing
     * of the current stream part. Subsequent parts will still be processed.
     * You can do this to set the severity (namespace of the inner element does not matter):
     * <pre>{@code <xsl:message><info>my message</info></xsl:message>}</pre>
     * Setting {@code terminate} trumps any severity set.
     */
    private void onXsltMessage(final XdmNode content, final boolean terminate, final SourceLocator locator) {

        boolean foundMsg = false;
        String msg = "";
        Severity severity = Severity.ERROR;

        if (terminate) {
            // terminate trumps any severity element name (see below)
            severity = Severity.FATAL_ERROR;
        } else {
            // We may have elements (or any valid xml) inside <xsl:message></xsl:message>
            if (content != null && content.children() != null) {
                for (final XdmNode child : content.children()) {
                    if (child != null) {
                        final XdmNodeKind nodeKind = child.getNodeKind();
                        if (nodeKind == XdmNodeKind.TEXT) {
                            // Just text inside the msg
                            // <xsl:message>my message</xsl:message>
                            msg = child.getStringValue();
                            break;
                        } else if (nodeKind == XdmNodeKind.ELEMENT) {
                            // Found an element so try to use the elm name to set the severity, e.g.
                            // <xsl:message><error>my message</error></xsl:message>
                            // We don't care about the namespace
                            final String localElmName = NullSafe.get(child.getNodeName(), QName::getLocalName);
                            final Severity elmSeverity = Severity.getSeverity(localElmName);
                            if (elmSeverity != null) {
                                severity = elmSeverity;
                                msg = child.getStringValue();
                                foundMsg = true;
                                break;
                            }
                        }
                    }
                    // Not found on first child so just break out and use getStringValue
                    break;
                }
            }
        }
        if (!foundMsg) {
            msg = NullSafe.getOrElse(content, XdmNode::getStringValue, "");
        }

        if (NullSafe.isEmptyString(msg)) {
            msg = "NO MESSAGE";
        }

        errorReceiverProxy.log(
                severity,
                locationFactory.create(locator),
                getElementId(),
                msg,
                ErrorType.CODE,
                null);

        if (terminate) {
            throw ProcessException.create(
                    "Processing terminated due to <xsl:message terminate=\"yes\"> with message: " + msg);
        }
    }

    private Location getLocation(final Throwable e) {
        if (e instanceof TransformerException) {
            return locationFactory.create(((TransformerException) e).getLocator());
        }

        return null;
    }

    private Throwable unwrapException(final Throwable e) {
        Throwable cause = e;

        while (cause != null && cause.getCause() != null && cause != cause.getCause()) {
            // Return cause early if it is a TransformerException or ProcessException
            if (cause instanceof TransformerException || cause instanceof ProcessException) {
                return cause;
            }

            cause = cause.getCause();
        }

        return cause;
    }

    /**
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #endPrefixMapping
     * @see #startElement
     * @see stroom.pipeline.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (handler != null) {
            handler.startPrefixMapping(prefix, uri);
        } else if (passThrough) {
            super.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * the prefix that was being mapped. This is the empty string when a default
     * mapping scope ends.
     *
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #startPrefixMapping
     * @see #endElement
     * @see stroom.pipeline.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (handler != null) {
            handler.endPrefixMapping(prefix);
        } else if (passThrough) {
            super.endPrefixMapping(prefix);
        }
    }

    /**
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified name (with prefix), or the empty string if
     *                  qualified names are not available
     * @param atts      the attributes attached to the element. If there are no
     *                  attributes, it shall be an empty Attributes object. The value
     *                  of this object after startElement returns is undefined
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (handler != null) {
            elementCount++;
            if (elementCount > maxElementCount) {
                final String message =
                        "Max element count of " +
                        maxElementCount +
                        " has been exceeded. Please ensure a split filter is present and is configured " +
                        "correctly for this pipeline.";

                final ProcessException exception = ProcessException.create(message);
                if (pipelineContext.isStepping()) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), exception.getMessage(),
                            exception);
                } else {
                    errorReceiverProxy.log(Severity.FATAL_ERROR, locationFactory.create(locator), getElementId(),
                            exception.getMessage(), exception);
                }

                // If we aren't stepping then throw an exception to terminate
                // early.
                if (!pipelineContext.isStepping()) {
                    throw LoggedException.wrap(exception);
                }
            }

            handler.startElement(uri, localName, qName, atts);
        } else if (passThrough) {
            super.startElement(uri, localName, qName, atts);
        }
    }

    /**
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified XML name (with prefix), or the empty string if
     *                  qualified names are not available
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (handler != null) {
            handler.endElement(uri, localName, qName);
        } else if (passThrough) {
            super.endElement(uri, localName, qName);
        }
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.characters(ch, start, length);
        } else if (passThrough) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #characters
     * @see stroom.pipeline.filter.AbstractXMLFilter#ignorableWhitespace(char[],
     * int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.ignorableWhitespace(ch, start, length);
        } else if (passThrough) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    /**
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.filter.AbstractXMLFilter#processingInstruction(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        if (handler != null) {
            handler.processingInstruction(target, data);
        } else if (passThrough) {
            super.processingInstruction(target, data);
        }
    }

    /**
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see stroom.pipeline.filter.AbstractXMLFilter#skippedEntity(java.lang.String)
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        if (handler != null) {
            handler.skippedEntity(name);
        } else if (passThrough) {
            super.skippedEntity(name);
        }
    }

    public boolean isXsltRequired() {
        return xsltRequired;
    }

    public void setXsltRequired(final boolean xsltRequired) {
        this.xsltRequired = xsltRequired;
    }

    public List<PipelineReference> getPipelineReferences() {
        return pipelineReferences;
    }

    @PipelineProperty(description = "The XSLT to use.", displayPriority = 1)
    @PipelinePropertyDocRef(types = XsltDoc.TYPE)
    public void setXslt(final DocRef xsltRef) {
        this.xsltRef = xsltRef;
    }

    @PipelineProperty(description = "A name pattern to load XSLT dynamically.", displayPriority = 2)
    public void setXsltNamePattern(final String xsltNamePattern) {
        this.xsltNamePattern = xsltNamePattern;
    }

    @PipelineProperty(description = "If XSLT cannot be found to match the name pattern suppress warnings.",
            defaultValue = "false", displayPriority = 3)
    public void setSuppressXSLTNotFoundWarnings(final boolean suppressXSLTNotFoundWarnings) {
        this.suppressXSLTNotFoundWarnings = suppressXSLTNotFoundWarnings;
    }

    @PipelineProperty(description = "A list of places to load reference data from if required.", displayPriority = 5)
    public void setPipelineReference(final PipelineReference pipelineReference) {
        if (pipelineReferences == null) {
            pipelineReferences = new ArrayList<>();
        }

        pipelineReferences.add(pipelineReference);
    }

    @PipelineProperty(
            description = "Advanced: Choose whether or not you want to use cached XSLT templates to improve " +
                          "performance.",
            defaultValue = "true",
            displayPriority = 4)
    public void setUsePool(final boolean usePool) {
        this.usePool = usePool;
    }

    @Override
    public void setInjectedCode(final String injectedCode) {
        this.injectedCode = injectedCode;
    }

    public XsltDoc loadXsltDoc() {
        final DocRef docRef = findDoc(
                getFeedName(),
                getPipelineName(),
                message -> errorReceiverProxy.log(Severity.WARNING, null, getElementId(), message, null));
        if (docRef != null) {
            final XsltDoc xsltDoc = xsltStore.readDocument(docRef);
            if (xsltDoc == null) {
                final String message = "XSLT \"" +
                                       docRef.getName() +
                                       "\" appears to have been deleted";
                throw ProcessException.create(message);
            }

            return xsltDoc;
        }

        return null;
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
                xsltRef,
                xsltNamePattern,
                feedName,
                pipelineName,
                errorConsumer,
                suppressXSLTNotFoundWarnings);
    }
}
