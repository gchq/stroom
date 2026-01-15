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
 * A class used to store a skippedEntity SAX event.
 */
public final class SkippedEntity extends BaseEvent {

    private static final String SKIPPED_ENTITY = "skippedEntity:";

    private final String name;

    /**
     * Stores a skippedEntity SAX event.
     *
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     */
    public SkippedEntity(final String name) {
        this.name = name;
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
        handler.skippedEntity(name);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(SKIPPED_ENTITY);
        sb.append(name);

        return sb.toString();
    }

    @Override
    public boolean isSkippedEntity() {
        return true;
    }
}
