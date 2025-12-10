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
import net.sf.saxon.om.Sequence;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class StroomExtensionMetaFunctionCall extends StroomExtensionFunctionCall {

    private static final AttributesImpl EMPTY_ATTRIBUTES = new AttributesImpl();
    private static final String URI = "stroom-meta";
    private static final String KEY_ATTRIBUTE_NAME = "key";

    Sequence createMetaSequence(final XPathContext context, final String elementName,
                                final Set<Entry<String, String>> meta)
            throws SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        contentHandler.startDocument();
        startElement(contentHandler, elementName);

        final SequencedSet<Entry<String, String>> sortedMeta = meta.stream()
                .sorted(Entry.comparingByKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (final Entry<String, String> metaEntry : sortedMeta) {
            data(contentHandler, metaEntry.getKey(), metaEntry.getValue());
        }
        endElement(contentHandler, elementName);
        contentHandler.endDocument();

        final Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
    }

    private void data(final ReceivingContentHandler contentHandler, final String key, final String value)
            throws SAXException {
        final AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(URI, KEY_ATTRIBUTE_NAME, KEY_ATTRIBUTE_NAME, "string", key);
        startElement(contentHandler, "string", attributes);
        characters(contentHandler, String.valueOf(value));
        endElement(contentHandler, "string");
    }

    private void startElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.startElement(URI, name, name, EMPTY_ATTRIBUTES);
    }

    private void startElement(final ReceivingContentHandler contentHandler, final String name,
                              final AttributesImpl attributes) throws SAXException {
        contentHandler.startElement(URI, name, name, attributes);
    }

    private void endElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.endElement(URI, name, name);
    }

    private void characters(final ReceivingContentHandler contentHandler, final String characters) throws SAXException {
        final char[] chars = characters.toCharArray();
        contentHandler.characters(chars, 0, chars.length);
    }
}
