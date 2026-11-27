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

package stroom.mcp.impl.e2e;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// A catalogue of event types read item by item, through the REAL tools on a real Stroom (seen in a Haiku eval run
/// of case 69, 2026-11-20): each record a batch holding several alerts, the catalogue reading each alert as its own
/// record (`reading` with `for_each`); the draft drafted whole batches, said every event type was not in the
/// sample, and its rules matched nothing. Now the draft reads the items as the catalogue does: its rules'
/// conditions and fields relative to the item, the batch's own fields with `scope: record`, its counts of items;
/// processed, each alert is translated by its event type's rule, as many of each as the check counted.
@SuppressWarnings("unchecked")
class TestE2eCatalogueRepeatedItems extends AbstractE2eTest {

    private static final String FEED = "E2E-CAT-ITEMS";
    private static final String BUILD = "e2e-cat-items";
    private static final Map<String, String> TYPE_IDS = Map.of("malware", "Alert-Malware", "phishing",
            "Alert-Phishing", "policy", "Alert-PolicyViolation");
    private static final int ALERTS = 7;
    // Each record a batch of alerts, each alert told by an item of its tags (eval case 69's sample).
    private static final String BATCHES = """
            [{"gateway": "mx-gw1", "batch": 101, "alerts": [{"time": "2026-11-19T09:00:00Z", "tags": ["inbound", \
            "malware", "high"], "sender": "billing@evil.example", "recipient": "alice@corp.example", "subject": \
            "Invoice overdue"}, {"time": "2026-11-19T09:00:05Z", "tags": ["inbound", "phishing"], "sender": \
            "it-help@evil.example", "recipient": "bob@corp.example", "subject": "Reset your password"}]},
             {"gateway": "mx-gw2", "batch": 102, "alerts": [{"time": "2026-11-19T09:01:00Z", "tags": ["outbound", \
            "policy", "low"], "sender": "carol@corp.example", "recipient": "someone@partner.example", "subject": \
            "Customer list"}]},
             {"gateway": "mx-gw1", "batch": 103, "alerts": [{"time": "2026-11-19T09:02:00Z", "tags": ["malware", \
            "inbound"], "sender": "post@evil.example", "recipient": "dave@corp.example", "subject": "Your parcel"}, \
            {"time": "2026-11-19T09:02:30Z", "tags": ["high", "inbound", "phishing"], "sender": "ceo@evil.example", \
            "recipient": "erin@corp.example", "subject": "Urgent transfer"}, {"time": "2026-11-19T09:03:00Z", \
            "tags": ["policy", "outbound"], "sender": "bob@corp.example", "recipient": "me@home.example", \
            "subject": "Payroll"}]},
             {"gateway": "mx-gw2", "batch": 104, "alerts": [{"time": "2026-11-19T09:04:00Z", "tags": ["inbound", \
            "low", "phishing"], "sender": "prize@evil.example", "recipient": "alice@corp.example", "subject": \
            "You won"}]}]
            """;

    @Test
    void aCatalogueReadingItemsInsideRecordsIsDraftedAndTranslatedPerItem() {
        createFeed(BUILD, FEED);
        final List<Long> raw = List.of(upload(FEED, BATCHES));
        surveyed(BUILD, FEED, raw);

        // ### Catalogued item by item: the reading given, each alert counted, none uncatalogued.
        final Map<String, Object> reading = args("input", "json", "json_layout", "array", "for_each", "alerts");
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map.Entry<String, String> type : TYPE_IDS.entrySet()) {
            // The alert's type, which the schema requires, placed (decided by the user 2026-11-21).
            proposals.add(withKeys(eventType(type.getValue(), "Alert", "high", when("tags", "equals",
                    type.getKey())), "actor", "recipient", "placements", List.of(args("path", "Type", "value",
                    "Other"))));
        }
        final Map<String, Object> checked = json(CATALOGUE, args("build", BUILD, "stream_ids", raw, "reading",
                reading, "proposals", proposals));
        dump("proposals", checked);
        final Map<String, Long> counts = countsOf(checked);
        assertThat(counts).containsEntry("Alert-Malware", 2L).containsEntry("Alert-Phishing", 3L)
                .containsEntry("Alert-PolicyViolation", 2L);
        assertThat(map(checkOf(checked), "uncatalogued")).containsEntry("records", 0);
        confirmedCatalogue(BUILD);

        // ### The draft reads the items as the catalogue does: every event type in the sample, none uncatalogued.
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build",
                BUILD, "source_name", "Mail gateway", "system_name", "Mail gateway", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> drafted = map(draft, "mapping");
        assertThat(drafted).containsEntry("for_each", "alerts");
        assertThat(number(draft.get("records"))).isEqualTo(ALERTS);
        assertThat(jsonOf(draft.get("notes"))).doesNotContain("not in the sample", "no event type recognises");
        assertThat(jsonOf(drafted.get("events"))).contains("{\"field\":\"tags\",\"equals\":\"malware\"}")
                .contains("\"field\":\"gateway\",\"scope\":\"record\"");

        // ### Saved with what the schema still needs (the agent's part), stepped clean and processed: each alert
        // by its event type's rule, as many of each as counted, its batch's gateway its device.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "name",
                FEED + "-Events", "schema_version", VERSION, "stream_ids", raw, "mapping",
                completed(timeChosen(drafted))));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "name", FEED + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        assertThat(stepSample(pipeline, raw)).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, raw), "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize(ALERTS);
        assertThat(written.stream().collect(Collectors.groupingBy(event -> textAt(event, "EventDetail/TypeId"),
                Collectors.counting()))).isEqualTo(counts);
        assertThat(written).extracting(event -> textAt(event, "EventSource/Device/HostName"))
                .containsOnly("mx-gw1", "mx-gw2");
        // A list of values (an alert's tags) a Data per value, not one Data of them run together (decided by the
        // user, 2026-11-20).
        assertThat(jsonOf(drafted.get("events"))).contains("\"data_name\":\"tags\",\"field\":\"tags\","
                                                          + "\"repeat\":true");
        assertThat(tags(written.getFirst())).containsExactly("inbound", "malware", "high");
        assertThat(tags(written.get(1))).containsExactly("inbound", "phishing");
    }

    // The values of an Event's Data named tags, in order.
    private static List<String> tags(final Element event) {
        return descendants(event, "Data").stream()
                .filter(data -> "tags".equals(data.getAttribute("Name")))
                .map(data -> data.getAttribute("Value"))
                .toList();
    }

    // The draft with what the schema needs and the draft can't know, as the agent adds it: the device (the batch's
    // gateway); each alert's type the catalogue placed.
    private static Map<String, Object> completed(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final List<Map<String, Object>> common = new ArrayList<>(list(mapping, "common"));
        common.add(args("path", "EventSource/Device/HostName", "field", "gateway", "scope", "record"));
        mapping.put("common", common);
        return mapping;
    }
}
