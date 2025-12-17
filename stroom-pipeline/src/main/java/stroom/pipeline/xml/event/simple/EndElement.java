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

import stroom.pipeline.xml.event.BaseEvent;
import stroom.pipeline.xml.event.Event;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A class used to store an endElement SAX event.
 */
public final class EndElement extends BaseEvent {

    private static final String COLON = ":";
    private static final String END_ELEMENT = "endElement:";
    private static final String DELIMITER = "|";

    private final String uri;
    private final String localName;
    private String qName;
    private String prefix;

    /**
     * Stores an endElement SAX event.
     *
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified XML name (with prefix), or the empty string if
     *                  qualified names are not available
     */
    public EndElement(final String uri, final String localName, final String qName) {
        this.uri = uri;
        this.localName = localName;
        this.qName = qName;

        final int index = qName.indexOf(COLON);
        if (index != -1) {
            prefix = qName.substring(0, index);
        }
    }

    /**
     * Helper to build a simple end element.
     *
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @return built object
     */
    public static EndElement createSimple(final String uri, final String localName) {
        return new EndElement(uri, localName, localName);
    }

    public String getUri() {
        return uri;
    }

    public String getLocalName() {
        return localName;
    }

    public String getQName() {
        return qName;
    }

    /**
     * Fires a stored SAX event at the supplied content handler.
     *
     * @param handler The content handler to fire the SAX event at.
     * @throws SAXException Necessary to maintain the SAX event contract.
     * @see Event#fire(org.xml.sax.ContentHandler)
     */
    @Override
    public void fire(final ContentHandler handler) throws SAXException {
        handler.endElement(uri, localName, getQName());
    }

    @Override
    public boolean isEndElement() {
        return true;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(final String prefix) {
        if ((this.prefix != null && !this.prefix.equals(prefix)) || (prefix != null && !prefix.equals(this.prefix))) {
            this.prefix = prefix;

            // Update the qName.
            qName = createQName();
        }
    }

    /**
     * Creates a qName from the current prefix and localName.
     *
     * @return A newly created qName.
     */
    private String createQName() {
        if (prefix != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append(prefix);
            sb.append(COLON);
            sb.append(localName);
            return sb.toString();
        }

        return localName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(END_ELEMENT);
        sb.append(uri);
        sb.append(DELIMITER);
        sb.append(localName);
        sb.append(DELIMITER);
        sb.append(getQName());

        return sb.toString();
    }
}
