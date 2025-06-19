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

package stroom.pipeline.xml.event.simple;

import stroom.pipeline.xml.event.BaseEvent;
import stroom.pipeline.xml.event.Event;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A class used to store a characters SAX event.
 */
public final class Characters extends BaseEvent {

    private final String chars;

    /**
     * Used to build a simple Characters element outside of a SAX event.
     *
     * @param chars string
     * @return built object
     */
    private Characters(final String chars) {
        this.chars = chars;
    }

    /**
     * Stores a characters SAX event.
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     */
    public Characters(final char[] ch, final int start, final int length) {
        chars = new String(ch, start, length);
    }

    /**
     * Helper to build a simple Characters element.
     *
     * @param chars string
     * @return built object
     */
    public static Characters createSimple(final String chars) {
        return new Characters(chars);
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
        final char[] ch = chars.toCharArray();
        handler.characters(ch, 0, ch.length);
    }

    /**
     * Returns a string representation of this stores SAX event.
     *
     * @return A string representation of this stored SAX event.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return chars;
    }

    @Override
    public boolean isCharacters() {
        return true;
    }
}
