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

package stroom.cache.server;

import org.xml.sax.SAXException;

import stroom.refdata.MapStore;
import stroom.refdata.MapStoreBuilder;
import stroom.refdata.MapStoreBuilderImpl;
import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;

// TODO 2015-11-10: This is a copy of the test helper in stroom-process. If there's potential refactoring then we can do that, otherwise having a duplicate isn't too painful.
public class MapStoreTestUtil {
    public static MapStore createMapStore() {
        return createMapStore(createEventList());
    }

    public static MapStore createMapStore(final EventList eventList) {
        final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
        mapStoreBuilder.setEvents("TEST_MAP_NAME", "TEST_KEY_NAME", eventList, false);
        return mapStoreBuilder.getMapStore();
    }

    public static EventList createEventList() {
        final EventListBuilder builder = EventListBuilderFactory.createBuilder();
        for (int i = 0; i < 100; i++) {
            try {
                builder.startDocument();
                builder.startElement("testuri", "test", "test", null);
                builder.endElement("testuri", "test", "test");
                builder.endDocument();
            } catch (final SAXException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        final EventList eventList = builder.getEventList();
        builder.reset();

        return eventList;
    }
}
