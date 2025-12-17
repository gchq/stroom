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

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.net.URI;

class ParseUri extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseUri.class);

    private static final String EMPTY_STRING = "";
    private static final String NAMESPACE = "uri";

    private static final Attributes EMPTY_ATTS = new org.xml.sax.helpers.AttributesImpl();

    private ReceivingContentHandler contentHandler;

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            final String value = getSafeString(functionName, context, arguments, 0);
            if (value != null && !value.isEmpty()) {
                final URI uri = URI.create(value);

                final Configuration configuration = context.getConfiguration();
                final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
                final Builder builder = new TinyBuilder(pipe);

                contentHandler = new ReceivingContentHandler();
                contentHandler.setPipelineConfiguration(pipe);
                contentHandler.setReceiver(builder);

                startDocument();
                dataElement("authority", uri.getAuthority());
                dataElement("fragment", uri.getFragment());
                dataElement("host", uri.getHost());
                dataElement("path", uri.getPath());
                dataElement("port", uri.getPort() != -1
                        ? String.valueOf(uri.getPort())
                        : null);
                dataElement("query", uri.getQuery());
                dataElement("scheme", uri.getScheme());
                dataElement("schemeSpecificPart", uri.getSchemeSpecificPart());
                dataElement("userInfo", uri.getUserInfo());
                endDocument();

                final Sequence sequence = builder.getCurrentRoot();

                // Reset the builder, detaching it from the constructed
                // document.
                builder.reset();

                return sequence;
            }
        } catch (final XPathException | SAXException | RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            outputWarning(context, new StringBuilder("Problem parsing URI: " + e.getMessage()), e);
        }

        return EmptyAtomicSequence.getInstance();
    }

    private void startDocument() throws SAXException {
        contentHandler.startDocument();
        contentHandler.startPrefixMapping(EMPTY_STRING, NAMESPACE);

//        if (addRoot) {
//            startElement(XML_ELEMENT_MAP, null);
//        }
    }

    private void endDocument() throws SAXException {
//        if (addRoot) {
//            endElement(XML_ELEMENT_MAP);
//        }

        contentHandler.endPrefixMapping(EMPTY_STRING);
        contentHandler.endDocument();
    }

    private void startElement(final String elementName) throws SAXException {
        contentHandler.startElement(NAMESPACE, elementName, elementName, EMPTY_ATTS);
    }

    private void endElement(final String elementName) throws SAXException {
        contentHandler.endElement(NAMESPACE, elementName, elementName);
    }

    private void dataElement(final String elementName, final String value) throws SAXException {
        startElement(elementName);
        characters(value);
        endElement(elementName);
    }

    private void characters(final String value) throws SAXException {
        if (value != null) {
            final char[] ch = value.toCharArray();
            contentHandler.characters(ch, 0, ch.length);
        }
    }
}
