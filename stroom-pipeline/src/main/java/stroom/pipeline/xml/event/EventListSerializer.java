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

import stroom.pipeline.xml.event.simple.SimpleEventListBuilder;

import org.xml.sax.SAXException;

/**
 * Converts an {@link EventList} to and from the compact binary form used to persist stepping IO as
 * replayable events rather than re-serialised XML text.
 * <p>
 * A convenience over {@link SaxEventWriter}/{@link SaxEventReader} for callers that hold a whole
 * {@link EventList}; a capturing filter can instead encode a live stream straight into a
 * {@link SaxEventWriter}, and replay can fire bytes straight at a downstream handler via
 * {@link SaxEventReader}, without ever materialising an {@link EventList}.
 */
public final class EventListSerializer {

    private EventListSerializer() {
    }

    public static byte[] toBytes(final EventList eventList) {
        final SaxEventWriter writer = new SaxEventWriter();
        try {
            eventList.fire(writer);
        } catch (final SAXException e) {
            throw new RuntimeException("Unable to serialise event list", e);
        }
        return writer.toByteArray();
    }

    public static EventList fromBytes(final byte[] data) {
        final SimpleEventListBuilder builder = new SimpleEventListBuilder();
        try {
            SaxEventReader.replay(data, builder);
        } catch (final SAXException e) {
            throw new RuntimeException("Unable to deserialise event list", e);
        }
        return builder.getEventList();
    }
}
