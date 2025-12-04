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
 * A class used to store a processingInstruction SAX event.
 */
public final class ProcessingInstruction extends BaseEvent {

    private static final String PROCESSING_INSTRUCTION = "processingInstruction:";
    private static final String DELIMITER = "|";

    private final String target;
    private final String data;

    /**
     * Stores a processingInstruction SAX event.
     *
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     */
    public ProcessingInstruction(final String target, final String data) {
        this.target = target;
        this.data = data;
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
        handler.processingInstruction(target, data);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(PROCESSING_INSTRUCTION);
        sb.append(target);
        sb.append(DELIMITER);
        sb.append(data);

        return sb.toString();
    }

    @Override
    public boolean isProcessingInstruction() {
        return true;
    }
}
