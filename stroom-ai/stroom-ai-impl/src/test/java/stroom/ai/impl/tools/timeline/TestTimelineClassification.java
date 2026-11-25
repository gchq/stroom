/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.TimelineClassification.Fields;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimelineClassification {

    private static final Map<Integer, Fields> FIELDS = Map.of(0, new Fields(null, null, "TypeId"));

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Map<String, String>> extras = new LinkedHashMap<>();
    private final Map<String, String> xml = new LinkedHashMap<>();

    @Test
    void aCodeIsClassifiedByItsSampledEventsMostCommonSchemaType() {
        event("1", "doc-op", "<Event><EventDetail><TypeId>doc-op</TypeId><View/></EventDetail></Event>");
        event("2", "doc-op", "<Event><EventDetail><TypeId>doc-op</TypeId><Update/></EventDetail></Event>");
        event("3", "doc-op", "<Event><EventDetail><TypeId>doc-op</TypeId><Update/></EventDetail></Event>");
        // Its XML not read: classified with the rest of its code.
        event("4", "doc-op", null);

        final Map<Integer, Map<String, Classified>> codes = TimelineClassification.codesFromXml(events, extras,
                FIELDS, xml);

        assertThat(codes.get(0)).containsOnlyKeys("doc-op");
        assertThat(codes.get(0).get("doc-op").element()).isEqualTo("Update");
    }

    @Test
    void aTieGoesToTheEarliestAndEventsWithNoCodeOrNoElementAreLeftOut() {
        event("1", "net", "<Event><EventDetail><TypeId>net</TypeId><Network><Action>Open</Action></Network>"
                          + "</EventDetail></Event>");
        event("2", "net", "<Event><EventDetail><TypeId>net</TypeId><Send/></EventDetail></Event>");
        event("3", null, "<Event><EventDetail><View/></EventDetail></Event>");
        event("4", "odd", "<Event><EventDetail><TypeId>odd</TypeId></EventDetail></Event>");

        final Map<Integer, Map<String, Classified>> codes = TimelineClassification.codesFromXml(events, extras,
                FIELDS, xml);

        assertThat(codes.get(0)).containsOnlyKeys("net");
        assertThat(codes.get(0).get("net").element()).isEqualTo("Network");
        assertThat(TimelineClassification.codesFromXml(events, extras, FIELDS, Map.of())).isEmpty();
        assertThat(TimelineClassification.codesFromXml(events, extras, Map.of(), xml)).isEmpty();
    }

    private void event(final String id, final String code, final String text) {
        events.add(new Event(Long.parseLong(id), 0, id, null, null, null, null, null, null, null, null, "9", id));
        final Map<String, String> extra = new LinkedHashMap<>();
        if (code != null) {
            extra.put("TypeId", code);
        }
        extras.put(id, extra);
        if (text != null) {
            xml.put("9:" + id, text);
        }
    }
}
