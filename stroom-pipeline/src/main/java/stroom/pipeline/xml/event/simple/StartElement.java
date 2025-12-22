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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A class used to store a startElement SAX event.
 */
public final class StartElement extends BaseEvent {

    private static final String COLON = ":";
    private static final String START_ELEMENT = "startElement:";
    private static final String COMMA = ",";
    private static final String DELIMITER = "|";

    private final String uri;
    private final String localName;
    private final Attributes atts;
    private String qName;
    private String prefix;

    /**
     * Stores a start element SAX event.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     */
    public StartElement(final String uri, final String localName, final String qName, final Attributes atts) {
        this.uri = uri;
        this.localName = localName;
        this.qName = qName;

        final int index = qName.indexOf(COLON);
        if (index != -1) {
            prefix = qName.substring(0, index);
        }

        if (atts != null) {
            this.atts = new AttributesImpl(atts);
        } else {
            this.atts = null;
        }
    }

    /**
     * Helper to build a simple start element.
     *
     * @param uri  of the element
     * @param name of the element
     * @return built object
     */
    public static StartElement createSimple(final String uri, final String name) {
        return new StartElement(uri, name, name, new AttributesImpl());
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return the localName
     */
    public String getLocalName() {
        return localName;
    }

    /**
     * @return the name
     */
    public String getQName() {
        return qName;
    }

    /**
     * @return the attributes
     */
    public Attributes getAttributes() {
        return atts;
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
        handler.startElement(uri, localName, getQName(), atts);
    }

    @Override
    public boolean isStartElement() {
        return true;
    }

    /**
     * @return The prefix.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @param prefix the prefix to set.
     */
    public void setPrefix(final String prefix) {
        if ((this.prefix != null && !this.prefix.equals(prefix)) || (prefix != null && !prefix.equals(this.prefix))) {
            if (prefix != null) {
                this.prefix = prefix;
            } else {
                this.prefix = null;
            }

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

    /**
     * Returns a string representation of this stores SAX event.
     *
     * @return A string representation of this stored SAX event.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(START_ELEMENT);
        sb.append(uri);
        sb.append(DELIMITER);
        sb.append(localName);
        sb.append(DELIMITER);
        sb.append(getQName());
        sb.append(DELIMITER);

        if (atts != null) {
            for (int i = 0; i < atts.getLength(); i++) {
                sb.append(atts.getURI(i));
                sb.append(COMMA);
                sb.append(atts.getLocalName(i));
                sb.append(COMMA);
                sb.append(atts.getQName(i));
                sb.append(COMMA);
                sb.append(atts.getType(i));
                sb.append(COMMA);
                sb.append(atts.getValue(i));
            }
        }

        return sb.toString();
    }
}
