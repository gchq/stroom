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
 * A class used to store an ignorableWhitespace SAX event.
 */
public final class IgnorableWhitespace extends BaseEvent {

    private static final String IGNORABLE_WHITESPACE = "ignorableWhitespace:";

    private final String chars;

    /**
     * Used to build a simple IgnorableWhitespace element outside of a SAX
     * event.
     */
    private IgnorableWhitespace(final String chars) {
        this.chars = chars;
    }

    /**
     * Stores an ignorableWhitespace SAX event.
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     */
    public IgnorableWhitespace(final char[] ch, final int start, final int length) {
        chars = new String(ch, start, length);
    }

    /**
     * Helper to build a simple IgnorableWhitespace element.
     */
    public static IgnorableWhitespace createSimple(final String chars) {
        return new IgnorableWhitespace(chars);
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
        handler.ignorableWhitespace(ch, 0, ch.length);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(IGNORABLE_WHITESPACE);
        sb.append(chars);
        return sb.toString();
    }

    @Override
    public boolean isIgnorableWhitespace() {
        return true;
    }
}
