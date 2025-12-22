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

package stroom.pipeline.record;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.HasElementId;
import stroom.pipeline.filter.XMLFilterAdaptor;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.pipeline.xml.event.simple.StartPrefixMapping;
import stroom.util.io.ByteSlice;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;
import stroom.util.xml.TransformerFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.CharArrayWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * This filter is used to buffer SAX events in memory if required. Having
 * buffered SAX events this filter can then fire them at a content handler.
 */
public class XMLRecordEmitter extends XMLFilterAdaptor implements HasElementId {

    private final SAXTransformerFactory transformerFactory = (SAXTransformerFactory) TransformerFactoryFactory
            .newInstance();
    private final MyWriter outputStreamWriter = new MyWriter(1000);
    private final List<DestinationProvider> appenders;
    private final List<StartPrefixMapping> prefixList = new ArrayList<>();
    private LocationFactory locationFactory;
    private ElementId id;

    private ContentHandler handler;
    private int depth;
    private StartElement root;
    private StartPrefixMapping[] prefixes;
    private ErrorReceiver errorReceiver;
    private Locator locator;
    private boolean indentOutput = false;
    private String encoding;
    private String header;
    private String footer;
    private String body;

    public XMLRecordEmitter(final List<DestinationProvider> appenders) {
        this.appenders = appenders;
    }

    private ContentHandler createHandler() {
        ContentHandler handler = null;

        try {
            final ErrorListener errorListener = new ErrorListenerAdaptor(getElementId(), locationFactory,
                    errorReceiver);
            transformerFactory.setErrorListener(errorListener);

            final TransformerHandler th = transformerFactory.newTransformerHandler();
            final Transformer transformer = th.getTransformer();
            transformer.setErrorListener(errorListener);
            XMLUtil.setCommonOutputProperties(transformer, indentOutput);

            // Set the encoding to use.
            Charset charset = StreamUtil.DEFAULT_CHARSET;
            if (encoding != null && !encoding.isEmpty()) {
                try {
                    charset = Charset.forName(encoding);
                } catch (final RuntimeException e) {
                    errorReceiver.log(Severity.ERROR, null, getElementId(),
                            "Unsupported encoding '" + encoding + "', defaulting to UTF-8", e);
                }
            }
            transformer.setOutputProperty(OutputKeys.ENCODING, charset.name());

            th.setResult(new StreamResult(outputStreamWriter));
            th.setDocumentLocator(locator);
            handler = th;
        } catch (final TransformerConfigurationException e) {
            errorReceiver.log(Severity.FATAL_ERROR, null, getElementId(), e.getMessage(), e);
            throw LoggedException.wrap(e);
        }

        return handler;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        // Just remember the locator in case we start to output a document.
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    /**
     * We want to suppress prefix mappings for first level elements that we see
     * again.
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        // Store prefix mappings and output them if we haven't seen the root
        // element yet.
        if (root == null) {
            prefixList.add(new StartPrefixMapping(prefix, uri));

        } else if (depth > 2) {
            // If we are deeper than the root element then treat prefix mappings
            // as normal.
            handler.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * Don't end prefix mappings if we are at the root element. Leave the end
     * processing method to do this.
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (depth > 2) {
            handler.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        depth++;

        if (root == null) {
            root = new StartElement(uri, localName, qName, atts);
            prefixes = prefixList.toArray(new StartPrefixMapping[0]);
        }

        if (depth > 2) {
            handler.startElement(uri, localName, qName, atts);

        } else if (depth == 2) {
            handler = createHandler();
            handler.startDocument();
            for (int i = 0; i < prefixes.length; i++) {
                final StartPrefixMapping prefixMapping = prefixes[i];
                handler.startPrefixMapping(prefixMapping.getPrefix(), prefixMapping.getUri());
            }
            handler.startElement(root.getUri(), root.getLocalName(), root.getQName(), root.getAttributes());

            // Get the header
            header = getHeader();

            handler.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (depth > 2) {
            handler.endElement(uri, localName, qName);

        } else if (depth == 2) {
            handler.endElement(uri, localName, qName);

            // Get the body
            body = getBody();

            handler.endElement(root.getUri(), root.getLocalName(), root.getQName());
            for (int i = prefixes.length - 1; i >= 0; i--) {
                final StartPrefixMapping prefixMapping = prefixes[i];
                handler.endPrefixMapping(prefixMapping.getPrefix());
            }
            handler.endDocument();

            // Get the footer
            footer = getFooter();

            final ByteSlice headerBytes = StreamUtil.getByteSlice(header);
            final ByteSlice footerBytes = StreamUtil.getByteSlice(footer);
            final ByteSlice bodyBytes = StreamUtil.getByteSlice(body);

            final Rec record = new Rec(headerBytes, footerBytes, bodyBytes);

            /// Reset
            header = null;
            footer = null;
            body = null;
            handler = null;
        }

        depth--;
    }

    private String getHeader() {
        if (outputStreamWriter.getCount() == 0) {
            return null;
        }

        String xml = null;

        // Write the missing closing tag that we won't have had yet.
        outputStreamWriter.append('>');
        outputStreamWriter.flush();

        final char[] buffer = outputStreamWriter.getBuffer();
        xml = new String(buffer, 0, outputStreamWriter.getCount());

        outputStreamWriter.reset();

        return xml;
    }

    private String getBody() {
        if (outputStreamWriter.getCount() == 0) {
            return null;
        }

        String xml = null;

        outputStreamWriter.flush();

        // Get content ignoring the first char as it will be the closing char
        // for the previous element.
        final char[] buffer = outputStreamWriter.getBuffer();
        xml = new String(buffer, 1, outputStreamWriter.getCount() - 1);

        outputStreamWriter.reset();

        return xml;
    }

    private String getFooter() {
        if (outputStreamWriter.getCount() == 0) {
            return null;
        }

        String xml = null;

        outputStreamWriter.flush();

        final char[] buffer = outputStreamWriter.getBuffer();
        xml = new String(buffer, 0, outputStreamWriter.getCount());

        outputStreamWriter.reset();

        return xml;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 2) {
            handler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 2) {
            handler.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public ElementId getElementId() {
        return id;
    }

    @Override
    public void setElementId(final ElementId id) {
        this.id = id;
    }

    public void setIndentOutput(final boolean indentOutput) {
        this.indentOutput = indentOutput;
    }

    public void setErrorReceiver(final ErrorReceiver errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    public void setLocationFactory(final LocationFactory locationFactory) {
        this.locationFactory = locationFactory;
    }

    public void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    private static class MyWriter extends CharArrayWriter {

        public MyWriter(final int initialSize) {
            super(initialSize);
        }

        public char[] getBuffer() {
            return buf;
        }

        public int getCount() {
            return count;
        }
    }
}
