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
 * A class used to store an endPrefixMapping SAX event.
 */
public final class EndPrefixMapping extends BaseEvent {

    private static final String END_PREFIX_MAPPING = "endPrefixMapping:";

    private final String prefix;

    /**
     * Stores an endPrefixMapping SAX event.
     *
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     */
    public EndPrefixMapping(final String prefix) {
        this.prefix = prefix;
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
        handler.endPrefixMapping(prefix);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(END_PREFIX_MAPPING);
        sb.append(prefix);

        return sb.toString();
    }

    @Override
    public boolean isEndPrefixMapping() {
        return true;
    }
}
