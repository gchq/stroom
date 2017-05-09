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

package stroom.streamtask.server;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;
import stroom.entity.server.util.XMLUtil;
import stroom.io.EncodingWriter;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.CharBuffer;
import stroom.util.io.StreamUtil;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes out XML and records segment boundaries as it goes.
 */
public class XMLSegmentWriter extends XMLFilterImpl {
    private ContentHandler handler;

    private boolean doneRoot = false;
    private int depth;

    private EncodingWriter outputStreamWriter;
    private SegmentOutputStream segmentOutputStream;
    private CharBuffer charBuffer = null;

    public XMLSegmentWriter(final OutputStream outputStream) throws SAXException {
        try {
            if (outputStream != null) {
                if (outputStream instanceof SegmentOutputStream) {
                    segmentOutputStream = (SegmentOutputStream) outputStream;
                }

                final TransformerHandler th = XMLUtil.createTransformerHandler(false);
                outputStreamWriter = new EncodingWriter(outputStream, StreamUtil.DEFAULT_CHARSET);
                th.setResult(new StreamResult(outputStreamWriter));

                handler = th;
            }
        } catch (final TransformerConfigurationException e) {
            throw new SAXException(e);
        }
    }

    public boolean isStripWhiteSpace() {
        return charBuffer != null;
    }

    public void setStripWhiteSpace(final boolean strip) {
        if (strip) {
            charBuffer = new CharBuffer();
        } else {
            charBuffer = null;
        }
    }

    public EncodingWriter getOutputStreamWriter() {
        return outputStreamWriter;
    }

    /**
     * @param locator
     *            an object that can return the location of any SAX document
     *            event
     *
     * @see org.xml.sax.Locator
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        if (handler != null) {
            handler.setDocumentLocator(locator);
        }
        super.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        if (handler != null) {
            handler.startDocument();
        }
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        if (handler != null) {
            handler.endDocument();
        }
        super.endDocument();
    }

    /**
     * @param prefix
     *            the Namespace prefix being declared. An empty string is used
     *            for the default element namespace, which has no prefix.
     * @param uri
     *            the Namespace URI the prefix is mapped to
     *
     * @see #endPrefixMapping
     * @see #startElement
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (handler != null) {
            handler.startPrefixMapping(prefix, uri);
        }
        super.startPrefixMapping(prefix, uri);
    }

    /**
     * @param prefix
     *            the prefix that was being mapped. This is the empty string
     *            when a default mapping scope ends.
     *
     * @see #startPrefixMapping
     * @see #endElement
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (handler != null) {
            handler.endPrefixMapping(prefix);
        }
        super.endPrefixMapping(prefix);
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified name (with prefix), or the empty string if
     *            qualified names are not available
     * @param atts
     *            the attributes attached to the element. If there are no
     *            attributes, it shall be an empty Attributes object. The value
     *            of this object after startElement returns is undefined
     *
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (handler != null) {
            outputAnyCharBuffer();

            // Increase the element depth.
            depth++;

            // If depth is 2 then we are entering an event.
            if (depth == 2 && !doneRoot && segmentOutputStream != null) {
                try {
                    doneRoot = true;

                    // If the last element was a start element then we
                    // need to compensate for the fact the previous closing
                    // start element bracket '>' will not have been written
                    // yet.
                    outputStreamWriter.flush();
                    segmentOutputStream.addSegment(segmentOutputStream.getPosition() + 1);
                } catch (final IOException e) {
                    throw new SAXException(e);
                }
            }

            handler.startElement(uri, localName, qName, atts);
        }
        super.startElement(uri, localName, qName, atts);
    }

    private void outputAnyCharBuffer() throws SAXException {
        // Are we buffering content to strip white space out ?
        if (charBuffer != null) {
            if (handler != null) {
                if (!charBuffer.isBlank()) {
                    final char[] ch = charBuffer.toCharArray();
                    handler.characters(ch, 0, ch.length);
                }
            }
            charBuffer.clear();
        }
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified XML name (with prefix), or the empty string if
     *            qualified names are not available
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (handler != null) {
            outputAnyCharBuffer();

            handler.endElement(uri, localName, qName);

            // Decrease the element depth
            depth--;

            // If depth = 1 then we have finished an event.
            if (depth == 1 && segmentOutputStream != null) {
                try {
                    outputStreamWriter.flush();
                    segmentOutputStream.addSegment();
                } catch (final IOException e) {
                    throw new SAXException(e);
                }
            }
        }
        super.endElement(uri, localName, qName);
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     *
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        // Buffer Chars?
        if (charBuffer == null) {
            if (handler != null) {
                handler.characters(ch, start, length);
            }
        } else {
            charBuffer.append(ch, start, length);
        }
        super.characters(ch, start, length);
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     *
     * @see #characters
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.ignorableWhitespace(ch, start, length);
        }
        super.ignorableWhitespace(ch, start, length);
    }

    /**
     * @param target
     *            the processing instruction target
     * @param data
     *            the processing instruction data, or null if none was supplied.
     *            The data does not include any whitespace separating it from
     *            the target
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        if (handler != null) {
            handler.processingInstruction(target, data);
        }
        super.processingInstruction(target, data);
    }

    /**
     * @param name
     *            the name of the skipped entity. If it is a parameter entity,
     *            the name will begin with '%', and if it is the external DTD
     *            subset, it will be the string "[dtd]"
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        if (handler != null) {
            handler.skippedEntity(name);
        }
        super.skippedEntity(name);
    }
}
