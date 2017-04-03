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

package stroom.refdata.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.spring.StroomSpringProfiles;
import org.springframework.context.annotation.Profile;
import org.xml.sax.SAXException;

import stroom.refdata.MapStore;
import stroom.refdata.MapStoreBuilder;
import stroom.refdata.MapStoreBuilderImpl;
import stroom.refdata.MapStoreCacheKey;
import stroom.refdata.ReferenceDataLoader;
import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;

@Profile(StroomSpringProfiles.TEST)
public class MockReferenceDataLoader implements ReferenceDataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockReferenceDataLoader.class);

    @Override
    public MapStore load(final MapStoreCacheKey effectiveFeed) {
        final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
        final EventListBuilder builder = EventListBuilderFactory.createBuilder();
        for (int i = 0; i < 510; i++) {
            try {
                builder.startElement("", "test", "test", null);
                final char[] ch = ("This is a test char array for event list " + i).toCharArray();
                builder.characters(ch, 0, ch.length);
                builder.endElement("", "test", "test");

                final EventList eventList = builder.getEventList();
                builder.reset();

                mapStoreBuilder.setEvents("TEST_MAP", "TEST_KEY_" + (i + 1), eventList, false);

            } catch (final SAXException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return mapStoreBuilder.getMapStore();
    }
}
