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

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.util.shared.Severity;
import stroom.util.xml.XMLUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * A filter used to sample the output produced by SAX events at any point in the
 * XML pipeline. Many instances of this filter can be used.
 */
public abstract class AbstractSamplingFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSamplingFilter.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private ByteArrayOutputStream outputStream;

    private TransformerHandler handler;
    private ErrorListener errorListener;

    public AbstractSamplingFilter(final ErrorReceiverProxy errorReceiverProxy,
                                  final LocationFactoryProxy locationFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
    }

    /**
     * @throws SAXException Not thrown.
     * @see AbstractXMLFilter#startProcessing()
     */
    @Override
    public void startProcessing() {
        errorListener = new ErrorListenerAdaptor(getElementId(), locationFactory, errorReceiverProxy);

        try {
            this.handler = XMLUtil.createTransformerHandler(errorListener, false);

        } catch (final TransformerConfigurationException e) {
            errorReceiverProxy.log(Severity.FATAL_ERROR,
                    locationFactory.create(e.getLocator().getLineNumber(), e.getLocator().getColumnNumber()),
                    getElementId(), e.getMessage(), e);
            throw LoggedException.wrap(e);
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        super.endProcessing();
    }

    /**
     * @param locator an object that can return the location of any SAX document
     *                event
     * @see Locator
     * @see AbstractXMLFilter#setDocumentLocator(Locator)
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        handler.setDocumentLocator(locator);
        super.setDocumentLocator(locator);
    }

    /**
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see #endDocument
     * @see AbstractXMLFilter#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        this.outputStream = new ByteArrayOutputStream();
        handler.setResult(new StreamResult(outputStream));

        handler.startDocument();
        super.startDocument();
    }

    /**
     * @throws SAXException any SAX exception, possibly wrapping another exception
     * @see #startDocument
     * @see AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {

        try {
            handler.endDocument();

            outputStream.flush();
            outputStream.close();

            if (LOGGER.isDebugEnabled()) {
                try {
                    LOGGER.debug(XMLUtil.prettyPrintXML(outputStream.toString()));
                } catch (final RuntimeException e) {
                    LOGGER.debug("Not XML");
                    LOGGER.debug(outputStream.toString());
                }
            }

        } catch (final IOException e) {
            try {
                errorListener.fatalError(new TransformerException(e.getMessage()));
            } catch (final TransformerException te) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), te.getMessage(), te);
            }
        } finally {
            super.endDocument();
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
     * @see org.xml.sax.helpers.AttributesImpl
     * @see AbstractXMLFilter#startElement(String,
     * String, String, Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        handler.startElement(uri, localName, qName, atts);
        super.startElement(uri, localName, qName, atts);
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
        handler.endElement(uri, localName, qName);
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
    public void processingInstruction(final String target, final String data) throws SAXException {
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

    /**
     * @return The recorded output as a string.
     */
    public String getOutput() {
        return outputStream.toString();
    }
}
