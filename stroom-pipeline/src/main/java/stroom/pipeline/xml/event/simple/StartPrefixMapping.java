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
 * A class used to store a startPrefixMapping SAX event.
 */
public final class StartPrefixMapping extends BaseEvent {

    private static final String START_PREFIX_MAPPING = "startPrefixMapping:";
    private static final String DELIMITER = "|";

    private final String prefix;
    private final String uri;

    /**
     * Stores a startPrefixMapping SAX event.
     *
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     */
    public StartPrefixMapping(final String prefix, final String uri) {
        this.prefix = prefix;
        this.uri = uri;
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
        handler.startPrefixMapping(prefix, uri);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(START_PREFIX_MAPPING);
        sb.append(prefix);
        sb.append(DELIMITER);
        sb.append(uri);

        return sb.toString();
    }

    @Override
    public boolean isStartPrefixMapping() {
        return true;
    }
}
