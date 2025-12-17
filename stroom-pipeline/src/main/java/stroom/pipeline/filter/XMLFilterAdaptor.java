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

import stroom.pipeline.factory.Processor;
import stroom.task.api.Terminator;
import stroom.util.shared.ElementId;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.Collections;
import java.util.List;

public class XMLFilterAdaptor implements XMLFilter {

    @Override
    public void startProcessing() {
    }

    @Override
    public void endProcessing() {
    }

    @Override
    public void startStream() {
    }

    @Override
    public void endStream() {
    }

    @Override
    public List<Processor> createProcessors() {
        return Collections.emptyList();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
    }

    @Override
    public ElementId getElementId() {
        return null;
    }

    @Override
    public void setElementId(final ElementId elementId) {
    }

    @Override
    public void setTerminator(final Terminator terminator) {
    }
}
