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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.draft.DraftMapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The events written from a draft whose list fields it drafted as Data groups: a Data per item of a comma list, and
/// a Data per object of a JSON array holding a Data per key.
class TestDraftedListGroups extends XsltRuns {

    private static final String SAMPLE = """
            [{"host": "mx01", "time": "2026-10-01T08:00:00Z", "sender": "jane", "recipients": "a@mail.com,b@mail.com",
              "attachments": [{"name": "q3.xlsx", "size": 10}, {"name": "notes.txt"}]},
             {"host": "mx01", "time": "2026-10-01T08:05:00Z", "sender": "jane", "recipients": "c@mail.com",
              "attachments": []},
             {"host": "mx01", "time": "2026-10-01T08:07:00Z", "sender": "bob", "recipients": "d@mail.com, e@mail.com",
              "attachments": [{"name": "x.pdf", "size": 3}]}]
            """;

    // The draft's mapping with its rules' Unknown allowed, as the agent would leave it to test.
    private static Map<String, Object> decided(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(JsonValues.map(draft.get("mapping")));
        final List<Object> events = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(rule);
            copy.put("allow_unknown", "kept unknown for the test");
            events.add(copy);
        }
        mapping.put("events", events);
        return mapping;
    }

    @Test
    void eachItemOfAListAndEachObjectOfAnArrayIsAData() {
        final Map<String, Object> mapping = decided(DraftMapping.draft(SAMPLE, "Mail", "Mail", "Prod", null, null,
                null));
        final XsltGenerator.Generated result = XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA,
                "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="host">mx01</string><string key="time">2026-10-01T08:00:00Z</string>\
                <string key="sender">jane</string>\
                <string key="recipients">a@mail.com,b@mail.com</string>
                <array key="attachments"><map><string key="name">q3.xlsx</string><number key="size">10</number>\
                </map><map><string key="name">notes.txt</string></map></array>
                </map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement event = children(events, "Event").getFirst();
        assertThat(having(event, EVT, "Data", "Name", "recipients"))
                .extracting(list -> children(list, "Data").getFirst().get("Name") + "="
                                    + children(list, "Data").getFirst().get("Value"))
                .containsExactly("recipient=a@mail.com", "recipient=b@mail.com");
        assertThat(having(event, EVT, "Data", "Name", "attachments"))
                .extracting(attachment -> children(attachment, "Data").stream()
                        .map(data -> data.get("Name") + "=" + data.get("Value")).toList())
                .containsExactly(List.of("name=q3.xlsx", "size=10"), List.of("name=notes.txt"));
    }
}
