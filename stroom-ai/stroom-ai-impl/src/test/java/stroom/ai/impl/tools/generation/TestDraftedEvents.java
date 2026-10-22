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

import stroom.ai.impl.tools.draft.DraftMapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The events the XSLT generated from a draft writes (the Stroom MCP server's `tests/test_draft.py`, run with plain
/// Saxon here, Stroom's format-date standing in as the time itself).
class TestDraftedEvents extends XsltRuns {

    private static final String FW_JSON = """
            {"timestamp":"2026-10-01T09:00:12+10:00","device":"FW-EDGE-01","event_type":"TRAFFIC","severity":"INFO",\
            "src_ip":"192.0.2.10","src_port":54321,"dst_ip":"198.51.100.20","dst_port":443,"protocol":"TCP",\
            "action":"ALLOW","rule_id":1001,"bytes_sent":1520,"bytes_received":8450,"username":"",\
            "message":"Outbound HTTPS connection allowed"}
            {"timestamp":"2026-10-01T09:01:00+10:00","device":"FW-EDGE-01","event_type":"LOGIN","severity":"INFO",\
            "src_ip":"192.0.2.11","src_port":1,"dst_ip":"198.51.100.1","dst_port":22,"protocol":"TCP",\
            "action":"ALLOW","rule_id":7,"bytes_sent":0,"bytes_received":0,"username":"alice",\
            "message":"Admin login"}
            """;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decided(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>((Map<String, Object>) draft.get("mapping"));
        final List<Object> events = new ArrayList<>();
        for (final Object item : (List<Object>) mapping.get("events")) {
            final Map<String, Object> rule = new LinkedHashMap<>((Map<String, Object>) item);
            if (((List<Map<String, Object>>) rule.get("fields")).stream()
                    .anyMatch(f -> ((String) f.get("path")).contains("/Unknown/"))) {
                rule.put("allow_unknown", "kept unknown for the test");
            }
            events.add(rule);
        }
        mapping.put("events", events);
        return mapping;
    }

    @Test
    void theDraftsEventsComeOutRight() {
        final Map<String, Object> mapping = decided(DraftMapping.draft(Map.of("fw.jsonl", FW_JSON),
                "FortiOS firewall", null, null, null, null, null));
        mapping.put("unmatched", "skip");
        final XsltGenerator.Generated result = XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA,
                "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        final String xml = "<map xmlns=\"http://www.w3.org/2013/XSL/json\"><map><string key=\"timestamp\">"
                           + "2026-10-01T09:01:00.000Z</string><string key=\"device\">FW</string><string "
                           + "key=\"event_type\">LOGIN</string><string key=\"username\">alice</string><string "
                           + "key=\"src_ip\">192.0.2.11</string><string key=\"protocol\">TCP</string></map></map>";
        final XmlElement events = transform(result.xslt().replace("stroom:format-date(", "string(")
                .replace(", 'yyyy-MM-dd''T''HH:mm:ssXXX')", ")"), xml);
        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(text(descendant(events, "Authenticate"), "User/Id")).isEqualTo("alice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theKeyValuePairsOfATextFieldComeOutAsTheirFields() {
        final Map<String, Object> drafts = json(resource("/stroom/ai/impl/tools/draft/draft_parity.json")
                .replaceFirst("^\\[", "{\"all\": [") + "}");
        final Map<String, Object> audit = ((List<Map<String, Object>>) drafts.get("all")).stream()
                .filter(entry -> "test audit".equals(entry.get("name")))
                .findFirst()
                .orElseThrow();
        final String text = ((Map<String, String>) audit.get("samples")).get("audit.xml");
        final Map<String, Object> mapping = decided(DraftMapping.draft(audit.get("samples"), "Secret Server",
                "Secret Server", "Prod", null, null, null));
        final XsltGenerator.Generated result = XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA,
                "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        final XmlElement events = transform(result.xslt().replace("stroom:format-date(", "string(")
                .replace(", 'yyyy-MM-dd''T''HH:mm:ss.SSSX')", ")"), "<Events>" + text + "</Events>");
        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> all = children(events, "Event");
        assertThat(all).hasSize(5);
        final XmlElement first = all.get(0);
        assertThat(text(first, "EventSource/Client/IPAddress")).isEqualTo("192.0.2.45");
        assertThat(text(descendant(first, "Authenticate"), "Action")).isEqualTo("Logon");
        assertThat(text(all.get(1), "EventSource/SessionId")).isEqualTo("6399");
        final Map<String, String> values = new LinkedHashMap<>();
        all.get(3).iterElements(EVT, "Data").forEach(d -> values.put(d.get("Name"), d.get("Value")));
        // A dash inside a value stays (only one before another key parts them), and a value stops before (Item
        // Id: ...).
        assertThat(values).containsEntry("secret", "Firewall - Edge Admin")
                .containsEntry("by_user", "domain.com\\riley.chen")
                .containsEntry("item_id", "224")
                .containsEntry("action", "View");
    }
}
