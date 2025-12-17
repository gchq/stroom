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

import stroom.pipeline.xml.event.Event;
import stroom.pipeline.xml.event.EventList;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

public class SimpleEventList implements EventList {

    private final List<Event> events = new ArrayList<>();

    public void add(final Event event) {
        events.add(event);
    }

    @Override
    public void fire(final ContentHandler handler) throws SAXException {
        for (final Event event : events) {
            event.fire(handler);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Event event : events) {
            sb.append(event.toString());
        }
        return sb.toString();
    }

    public List<Event> getEvents() {
        return events;
    }
}
