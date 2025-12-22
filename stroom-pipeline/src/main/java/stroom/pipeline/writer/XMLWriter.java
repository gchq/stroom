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

package stroom.pipeline.writer;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.pipeline.LocationFactory;
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
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.filter.DocFinder;
import stroom.pipeline.filter.NullXMLFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.xslt.XsltStore;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.io.PathCreator;
import stroom.util.shared.Severity;
import stroom.util.xml.XMLUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.s9api.XsltExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * Writes out XML and records segment boundaries as it goes.
 */
@ConfigurableElement(
        type = "XMLWriter",
        description = """
                Writer to convert XML events data into XML output in the specified character encoding.
                """,
        category = Category.WRITER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_WRITER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_XML)
public class XMLWriter extends AbstractWriter implements XMLFilter {

    public static final Logger LOGGER = LoggerFactory.getLogger(XMLWriter.class);

    private final LocationFactory locationFactory;

    //Injected props required for finding and compiling XSLT
    private final PathCreator pathCreator;
    final Provider<FeedHolder> feedHolder;
    final Provider<PipelineHolder> pipelineHolder;
    final DocRefInfoService docRefInfoService;

    private ContentHandler handler = NullXMLFilter.INSTANCE;

    private boolean doneElement;
    private int depth;
    private String rootElement;

    private boolean indentOutput = false;

    //XSL related props in order to support <xsl:output>
    private final XsltStore xsltStore;
    private final XsltPool xsltPool;
    private DocRef xsltRef;
    private String xsltNamePattern;
    private boolean suppressXSLTNotFoundWarnings;

    private byte[] header;
    private byte[] footer;

    private CharBufferWriter stringWriter;
    private BufferedWriter bufferedWriter;

    private boolean startedDocument;

    public XMLWriter() {
        this.locationFactory = null;
        this.xsltStore = null;
        this.xsltPool = null;
        this.pathCreator = null;
        this.feedHolder = null;
        this.pipelineHolder = null;
        this.docRefInfoService = null;
    }

    @Inject
    public XMLWriter(final ErrorReceiverProxy errorReceiverProxy,
                     final LocationFactory locationFactory,
                     final XsltStore xsltStore,
                     final XsltPool xsltPool,
                     final PathCreator pathCreator,
                     final Provider<FeedHolder> feedHolder,
                     final Provider<PipelineHolder> pipelineHolder,
                     final DocRefInfoService docRefInfoService) {
        super(errorReceiverProxy);
        this.locationFactory = locationFactory;
        this.xsltStore = xsltStore;
        this.xsltPool = xsltPool;
        this.pathCreator = pathCreator;
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    public void startProcessing() {
        //LOGGER.trace("startProcessing called");
        try {
            Properties outputProperties = null;

            final XsltDoc xslt = loadXsltDoc();
            // If we have found XSLT then get a template.
            if (xslt != null) {

                // If no XSLT has been provided then don't try and get compiled
                // XSLT for it.
                if (xslt.getData() != null && xslt.getData().trim().length() > 0) {
                    // Get compiled XSLT from the pool.
                    final ErrorReceiver errorReceiver = new ErrorReceiverIdDecorator(getElementId(),
                            getErrorReceiver());
                    final PoolItem<StoredXsltExecutable> poolItem = xsltPool.borrowConfiguredTemplate(
                            xslt, errorReceiver, locationFactory, List.of(), true);
                    final StoredXsltExecutable storedXsltExecutable = poolItem.getValue();
                    // Get the errors.
                    final StoredErrorReceiver storedErrors = storedXsltExecutable.getErrorReceiver();
                    // Get the XSLT executable.
                    final XsltExecutable xsltExecutable = storedXsltExecutable.getXsltExecutable();
                    if (storedErrors.getTotalErrors() > 0) {
                        // Replay any exceptions that were created when
                        // compiling the XSLT into the pipeline error handler.
                        storedErrors.replay(errorReceiver);
                    }

                    outputProperties = xsltExecutable.getUnderlyingCompiledStylesheet().getOutputProperties();
                }
            }

            stringWriter = new CharBufferWriter();
            bufferedWriter = new BufferedWriter(stringWriter);

            final ErrorListener errorListener = new ErrorListenerAdaptor(getElementId(), locationFactory,
                    getErrorReceiver());
            final TransformerHandler th = XMLUtil.createTransformerHandler(errorListener, indentOutput);

            if (outputProperties != null) {
                th.getTransformer().setOutputProperties(outputProperties);
            }
            th.setResult(new StreamResult(bufferedWriter));
            handler = th;
            startedDocument = false;

        } catch (final TransformerConfigurationException e) {
            fatal(e);
            throw LoggedException.wrap(e);
        }

        super.startProcessing();
    }

    public XsltDoc loadXsltDoc() {

        final DocFinder<XsltDoc> docFinder = new DocFinder<>(
                XsltDoc.TYPE,
                pathCreator,
                xsltStore,
                docRefInfoService);
        final DocRef docRef =
                docFinder.findDoc(
                        xsltRef,
                        xsltNamePattern,
                        getFeedName(),
                        getPipelineName(),
                        message -> getErrorReceiver().log(
                                Severity.WARNING, null, getElementId(), message, null),
                        suppressXSLTNotFoundWarnings);

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

    @Override
    public void startStream() {
        //LOGGER.trace("startStream called");
        super.startStream();
        doneElement = false;
    }

    @Override
    public void startDocument() throws SAXException {
        //if (LOGGER.isTraceEnabled()) {
        //    LOGGER.trace("startDocument called, buffer [%s]", truncateAndStripWhitespace(getBuffer()));
        //}
        try {
            //clear out the buffer in case we have a lone processing instruction from a previous empty split
            //as the sax processor will write a processing instruction whenever startDocument is called, potentially
            //leading to many of them if we have lots of empty splits before this
            bufferedWriter.flush();
            stringWriter.getBuffer().setLength(0);
        } catch (final IOException e) {
            throw new SAXException(e);
        } finally {
            if (!startedDocument) {
                startedDocument = true;
                handler.startDocument();
                super.startDocument();
            }
        }

    }

    @Override
    public void endDocument() throws SAXException {
        //if (LOGGER.isTraceEnabled()) {
        //    LOGGER.trace("endDocument called, buffer [%s]", truncateAndStripWhitespace(getBuffer()));
        //}

        if (startedDocument) {
            handler.endDocument();
            super.endDocument();
            startedDocument = false;
        }
    }

    /**
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     * @throws SAXException the client may throw an exception during processing
     * @see #endPrefixMapping
     * @see #startElement
     * @see AbstractXMLFilter#startPrefixMapping(String,
     * String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        handler.startPrefixMapping(prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }

    /**
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     * @throws SAXException the client may throw an exception during processing
     * @see #startPrefixMapping
     * @see #endElement
     * @see AbstractXMLFilter#endPrefixMapping(String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        handler.endPrefixMapping(prefix);
        super.endPrefixMapping(prefix);
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
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see #endElement
     * @see Attributes
     * @see AttributesImpl
     * @see AbstractXMLFilter#startElement(String,
     * String, String, Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        //if (LOGGER.isTraceEnabled()) {
        //    LOGGER.trace(String.format("startElement called for %s at depth %s, buffer [%s]",
        //            localName, depth, truncateAndStripWhitespace(getBuffer())));
        //}

        try {
            // If depth is 1 then we are entering an event.
            if (rootElement == null) {
                rootElement = localName;
            }

            if (depth == 1) {
                bufferedWriter.flush();
                final CharBuffer cb = stringWriter.getBuffer();

                if (!doneElement) {
                    doneElement = true;

                    if (cb.length() > 0) {
                        // Compensate for the fact that the writer will not have
                        // received a closing bracket as it waits to see if the
                        // current start element will be an empty element. We
                        // will always write root start/end elements as full
                        // elements and not as <elem /> so add the close bracket
                        // here.
                        cb.append('>');

                        // Only allow the header and footer to be set once.
                        if (this.header == null && this.footer == null) {
                            final StringBuilder footerBuilder = new StringBuilder();
                            footerBuilder.append("</");
                            footerBuilder.append(rootElement);
                            footerBuilder.append(">");

                            if (indentOutput) {
                                cb.append('\n');
                                footerBuilder.append('\n');
                            }

                            final String header = cb.toString();
                            final String footer = footerBuilder.toString();
                            //LOGGER.trace(String.format("Setting header [%s] and footer [%s]", header, footer));

                            this.header = header.getBytes(getCharset());
                            this.footer = footer.getBytes(getCharset());
                        }
                    }
                }
                cb.setLength(0);
            }

            // Increase the element depth.
            depth++;

            handler.startElement(uri, localName, qName, atts);

        } catch (final IOException e) {
            throw new SAXException(e);
        } finally {
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
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see AbstractXMLFilter#endElement(String,
     * String, String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        //if (LOGGER.isTraceEnabled()) {
        //    LOGGER.trace(String.format("endElement called for %s at depth %s, buffer [%s]",
        //            localName, depth, truncateAndStripWhitespace(getBuffer())));
        //}
        handler.endElement(uri, localName, qName);

        // Decrease the element depth
        depth--;

        try {
            if (depth <= 1) {
                bufferedWriter.flush();
                final CharBuffer cb = stringWriter.getBuffer();

                // If depth = 1 then we have finished an event.
                if (depth == 1) {
                    if (cb.length() > 0) {
                        // Compensate for the fact that the writer will now have
                        // received a closing bracket see comment in
                        // startElement() for an explanation.
                        cb.trimCharStart('>');

                        // Trim new lines off the character buffer.
                        cb.trimChar('\n');

                        // If we are indenting the output then add a new line
                        // after records.
                        if (indentOutput) {
                            cb.append('\n');
                        }

                        final String body = cb.toString();

                        borrowDestinations(header, footer);

                        //if (LOGGER.isTraceEnabled()) {
                        //    LOGGER.trace(String.format("Writing %s chars [%s] to destinations",
                        //            body.length(),
                        //            truncateAndStripWhitespace(body)));
                        //}
                        getWriter().write(body);
                        returnDestinations();
                    }
                } else {
                    doneElement = false;
                }

                cb.setLength(0);
            }
        } catch (final IOException e) {
            throw new SAXException(e);
        }

        super.endElement(uri, localName, qName);
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see Locator
     * @see AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        handler.characters(ch, start, length);
        super.characters(ch, start, length);
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see #characters
     * @see AbstractXMLFilter#ignorableWhitespace(char[],
     * int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        handler.ignorableWhitespace(ch, start, length);
        super.ignorableWhitespace(ch, start, length);
    }

    /**
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see AbstractXMLFilter#processingInstruction(String,
     * String)
     */
    @Override
    public void processingInstruction(final String target,
                                      final String data) throws SAXException {
        // Ensure we have started a document - this avoids some unexpected behaviour in XMLParser where we
        // receive processing instruction events before a startDocument() event, see gh-225.
        startDocument();

        handler.processingInstruction(target, data);
        super.processingInstruction(target, data);
    }

    /**
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see AbstractXMLFilter#skippedEntity(String)
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        handler.skippedEntity(name);
        super.skippedEntity(name);
    }

    @PipelineProperty(
            description = "Should output XML be indented and include new lines (pretty printed)?",
            defaultValue = "false",
            displayPriority = 1)
    public void setIndentOutput(final boolean indentOutput) {
        this.indentOutput = indentOutput;
    }

    @Override
    @PipelineProperty(
            description = "The output character encoding to use.",
            defaultValue = "UTF-8",
            displayPriority = 2)
    public void setEncoding(final String encoding) {
        super.setEncoding(encoding);
    }


    @PipelineProperty(
            description = "A previously saved XSLT, used to modify the output via xsl:output attributes.",
            displayPriority = 1)
    @PipelinePropertyDocRef(types = XsltDoc.TYPE)
    public void setXslt(final DocRef xsltRef) {
        this.xsltRef = xsltRef;
    }

    @PipelineProperty(
            description = "A name pattern for dynamic loading of an XSLT, that will modfy the output via " +
                          "xsl:output attributes.",
            displayPriority = 2)
    public void setXsltNamePattern(final String xsltNamePattern) {
        this.xsltNamePattern = xsltNamePattern;
    }

    @PipelineProperty(
            description = "If XSLT cannot be found to match the name pattern suppress warnings.",
            defaultValue = "false",
            displayPriority = 3)
    public void setSuppressXSLTNotFoundWarnings(final boolean suppressXSLTNotFoundWarnings) {
        this.suppressXSLTNotFoundWarnings = suppressXSLTNotFoundWarnings;
    }

    /**
     * Used when XSLT is supplied (for xsl:output)
     */
    private String getFeedName() {
        if (feedHolder != null) {
            final FeedHolder fh = feedHolder.get();
            if (fh != null) {
                return fh.getFeedName();
            }
        }
        return null;
    }

    /**
     * Used when XSLT is supplied (for xsl:output)
     */
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

    @SuppressWarnings("unused") //useful for debugging
    private static String truncateAndStripWhitespace(final String str) {
        //remove any line breaks and white space, accepting that white space in element text or attributes will be lost
        //but is this is intended for debugging that is ok.
        String truncatedStr = str
                .replaceAll("\\s+", "")
                .replace("\n", "");
        if (truncatedStr != null) {
            final int strLen = truncatedStr.length();
            if (strLen > 100) {
                truncatedStr = String.format("%s..TRUNCATED..%s",
                        truncatedStr.substring(0, 45),
                        truncatedStr.substring(strLen - 45));
            }
        } else {
            truncatedStr = "NULL";
        }
        return truncatedStr;
    }

    /**
     * Only for use in debugging as it will flush the stringWriter
     */
    @SuppressWarnings("unused") //useful for debugging
    private String getBuffer() {
        CharBuffer charBuffer = null;
        try {
            stringWriter.flush();
            charBuffer = stringWriter.getBuffer();
        } catch (final RuntimeException e) {
            LOGGER.warn("Ignoring error {}", e.getMessage());
        }
        return charBuffer != null
                ? charBuffer.toString()
                : "";
    }
}
