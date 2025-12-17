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

package stroom.pipeline.xml.event.simple;

import stroom.pipeline.xml.event.EventList;
import stroom.pipeline.xml.event.EventListBuilder;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class SimpleEventListBuilder implements EventListBuilder {

    private SimpleEventList eventList = new SimpleEventList();

    @Override
    public EventList getEventList() {
        return eventList;
    }

    @Override
    public void reset() {
        eventList = new SimpleEventList();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        eventList.add(new SetDocumentLocator(locator));
    }

    @Override
    public void startDocument() throws SAXException {
        eventList.add(new StartDocument());
    }

    @Override
    public void endDocument() throws SAXException {
        eventList.add(new EndDocument());
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        eventList.add(new StartPrefixMapping(prefix, uri));
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        eventList.add(new EndPrefixMapping(prefix));
    }

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts) throws SAXException {
        eventList.add(new StartElement(uri, localName, qName, atts));
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        eventList.add(new EndElement(uri, localName, qName));
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        eventList.add(new Characters(ch, start, length));
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        eventList.add(new IgnorableWhitespace(ch, start, length));
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        eventList.add(new ProcessingInstruction(target, data));
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        eventList.add(new SkippedEntity(name));
    }
}
