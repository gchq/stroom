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

package stroom.pipeline.xml.event;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * An interface to be implemented by all SAX event storage classes. This
 * interface specifies a method that all SAX event storage classes must
 * implement to fire their stored event at a supplied content handler.
 */
public interface Event {
    /**
     * Fires a stored SAX event at the supplied content handler.
     *
     * @param handler The content handler to fire the SAX event at.
     * @throws SAXException Necessary to maintain the SAX event contract.
     */
    void fire(final ContentHandler handler) throws SAXException;

    boolean isSetDocumentLocator();

    boolean isStartDocument();

    boolean isEndDocument();

    boolean isStartPrefixMapping();

    boolean isEndPrefixMapping();

    boolean isStartElement();

    boolean isEndElement();

    boolean isCharacters();

    boolean isIgnorableWhitespace();

    boolean isProcessingInstruction();

    boolean isSkippedEntity();
}
