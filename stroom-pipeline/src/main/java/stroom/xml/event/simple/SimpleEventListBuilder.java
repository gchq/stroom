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

package stroom.xml.event.simple;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;

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
    public void setDocumentLocator(Locator locator) {
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
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        eventList.add(new StartPrefixMapping(prefix, uri));
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        eventList.add(new EndPrefixMapping(prefix));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        eventList.add(new StartElement(uri, localName, qName, atts));
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        eventList.add(new EndElement(uri, localName, qName));
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        eventList.add(new Characters(ch, start, length));
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        eventList.add(new IgnorableWhitespace(ch, start, length));
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        eventList.add(new ProcessingInstruction(target, data));
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        eventList.add(new SkippedEntity(name));
    }
}
