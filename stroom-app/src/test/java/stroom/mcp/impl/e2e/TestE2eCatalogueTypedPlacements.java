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
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Typed placements decided in the catalogue (decided by the user 2026-11-21), through the REAL tools on a real
/// Stroom. Seen in chat 182 (Microsoft 365's audit log, seven event types): the translated events had almost nothing
/// typed in EventDetail (Update-InboxRule 0 typed leaves against 24 Data), as the catalogue placed no object and the
/// build was then given stand-ins. Here each event type places its fields at their schema homes (an Email's subject
/// and recipients, a Move's folders, an Update's group), a set refused while one leaves an element the schema
/// requires empty; drafted, built and processed, the events are schema-valid with those elements typed and the
/// fields left over kept as Data, each once.
@SuppressWarnings("unchecked")
class TestE2eCatalogueTypedPlacements extends AbstractE2eTest {

    private static final String FEED = "E2E-CAT-PLACED";
    private static final String BUILD = "e2e-cat-placed";
    // Microsoft 365 audit records (their fields as the audit log names them), a JSON array.
    private static final String AUDIT = """
            [{"CreationTime": "2026-11-01T10:00:00Z", "Operation": "Send", "Workload": "Exchange", \
            "UserId": "jo@acme.example", "ClientIP": "10.0.0.1", "Subject": "Quarterly report", \
            "Recipients": ["al@acme.example", "bo@acme.example"], "ResultStatus": "Succeeded"},
             {"CreationTime": "2026-11-01T10:01:00Z", "Operation": "Send", "Workload": "Exchange", \
            "UserId": "al@acme.example", "ClientIP": "10.0.0.2", "Subject": "Re: Quarterly report", \
            "Recipients": ["jo@acme.example"], "ResultStatus": "Succeeded"},
             {"CreationTime": "2026-11-01T10:02:00Z", "Operation": "MoveToDeletedItems", "Workload": "Exchange", \
            "UserId": "jo@acme.example", "ClientIP": "10.0.0.1", "SourceFolder": "Inbox", \
            "DestFolder": "Deleted Items", "ResultStatus": "Succeeded"},
             {"CreationTime": "2026-11-01T10:03:00Z", "Operation": "MemberAdded", "Workload": "MicrosoftTeams", \
            "UserId": "bo@acme.example", "ClientIP": "10.0.0.3", "TeamName": "Finance", \
            "TeamGuid": "19:abc@thread.skype", "ResultStatus": "Succeeded"}]
            """;

    @Test
    void placementsDecidedInTheCatalogueAreTypedInTheTranslatedEvents() {
        createFeed(BUILD, FEED);
        final List<Long> raw = List.of(upload(FEED, AUDIT));
        surveyed(BUILD, FEED, raw);

        // ### Proposed without what Update requires (its After): recorded, but not put to the user.
        final Map<String, Object> send = withKeys(eventType("Send-Mail", "Send", "high", when("Operation", "equals",
                "Send")), "actor", "UserId", "placements", List.of(
                args("path", "Source/User/EmailAddress", "field", "UserId"),
                args("path", "Destination/User", "for_each", args("array", "Recipients"), "fields",
                        List.of(args("path", "EmailAddress", "item", true))),
                args("path", "Payload/Email/Subject", "field", "Subject")));
        final Map<String, Object> move = withKeys(eventType("Move-MailToDeletedItems", "Move", "high",
                when("Operation", "equals", "MoveToDeletedItems")), "actor", "UserId", "placements", List.of(
                args("path", "Source/Folder/Path", "field", "SourceFolder"),
                args("path", "Destination/Folder/Path", "field", "DestFolder")));
        final Map<String, Object> unplaced = withKeys(eventType("Update-TeamMemberAdded", "Update", "high",
                when("Operation", "equals", "MemberAdded")), "actor", "UserId");
        final Map<String, Object> checked = proposed(BUILD, raw, List.of(send, move, unplaced));
        assertThat(checked).containsEntry("recorded", true);
        assertThat(jsonOf(checkOf(checked).get("unplaced"))).contains("Update-TeamMemberAdded",
                "EventDetail/Update/After is required by the schema");
        assertThat(replyText(CATALOGUE, args("build", BUILD, "confirm", true))).startsWith("Not put to the user")
                .contains("EventDetail/Update/After");

        // ### A misspelt field is refused naming the record field it may have meant; then placed as the records
        // spell it, and the set confirmed.
        assertThat(replyText(CATALOGUE, args("build", BUILD, "stream_ids", raw, "proposals", List.of(
                args("op", "change", "type_id", "Update-TeamMemberAdded", "placements", List.of(
                        args("path", "After/Group/Name", "field", "Teamname")))))))
                .contains("no record has the field Teamname", "TeamName");
        assertThat(proposed(BUILD, raw, List.of(args("op", "change", "type_id", "Update-TeamMemberAdded",
                "placements", List.of(args("path", "After/Group/Name", "field", "TeamName"),
                        args("path", "After/Group/Id", "field", "TeamGuid"))))))
                .containsEntry("recorded", true);
        confirmedCatalogue(BUILD);
        assertThat(notesDocText(BUILD)).contains("typed: TeamName -> After/Group/Name; TeamGuid -> After/Group/Id");

        // ### Drafted: the placements are typed entries of each event type's rule, the fields left over Data.
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", raw, "build",
                BUILD, "source_name", "Microsoft 365", "system_name", "Microsoft 365", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> drafted = map(draft, "mapping");
        // The event types' rules, the catch-all (whose records' fields are all Data) aside.
        final String rules = jsonOf(list(drafted, "events").stream()
                .filter(rule -> !Boolean.TRUE.equals(rule.get("catch_all")))
                .toList());
        assertThat(rules).contains("\"path\":\"EventDetail/Send/Payload/Email/Subject\",\"field\":\"Subject\"",
                "\"path\":\"EventDetail/Move/Source/Folder/Path\",\"field\":\"SourceFolder\"",
                "\"path\":\"EventDetail/Update/After/Group/Name\",\"field\":\"TeamName\"")
                .doesNotContain("\"data_name\":\"Subject\"", "\"data_name\":\"TeamName\"");

        // ### Built, stepped clean (schema-valid) and processed: each event's placed elements typed.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "name",
                FEED + "-Events", "schema_version", VERSION, "stream_ids", raw, "mapping",
                withDevice(timeChosen(drafted))));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "name", FEED + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        assertThat(stepSample(pipeline, raw)).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, raw), "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        final List<Element> written = children(parse(storedText(events.getFirst())).getDocumentElement(), "Event");
        assertThat(written).hasSize(4);
        final Map<String, Element> byType = new LinkedHashMap<>();
        written.forEach(event -> byType.putIfAbsent(textAt(event, "EventDetail/TypeId"), event));

        final Element sent = byType.get("Send-Mail");
        assertThat(textAt(sent, "EventDetail/Send/Payload/Email/Subject")).isEqualTo("Quarterly report");
        assertThat(textAt(sent, "EventDetail/Send/Source/User/EmailAddress")).isEqualTo("jo@acme.example");
        assertThat(descendants(sent, "EmailAddress").stream().map(Node::getTextContent).toList())
                .contains("al@acme.example", "bo@acme.example");
        assertThat(textAt(byType.get("Move-MailToDeletedItems"), "EventDetail/Move/Destination/Folder/Path"))
                .isEqualTo("Deleted Items");
        assertThat(textAt(byType.get("Update-TeamMemberAdded"), "EventDetail/Update/After/Group/Name"))
                .isEqualTo("Finance");

        // Typed leaves and Data below each action element, counted: what chat 182 lacked.
        assertThat(typed(sent)).isGreaterThanOrEqualTo(4);
        assertThat(typed(byType.get("Move-MailToDeletedItems"))).isGreaterThanOrEqualTo(2);
        assertThat(typed(byType.get("Update-TeamMemberAdded"))).isGreaterThanOrEqualTo(2);
        for (final Element event : written) {
            final List<String> dataNames = descendants(event, "Data").stream()
                    .map(data -> data.getAttribute("Name"))
                    .toList();
            assertThat(dataNames).as("each field kept as Data once").doesNotHaveDuplicates()
                    .doesNotContain("Subject", "SourceFolder", "DestFolder", "TeamName", "TeamGuid");
            // What is left is the records' own context (the workload, the result, the vendor's code).
            assertThat(dataNames).as("kept as Data").containsOnly("Workload", "ResultStatus", "Operation");
        }
    }

    // The typed leaves below an event's action element: elements holding text, not Data.
    private static int typed(final Element event) {
        final Element detail = children(event, "EventDetail").getFirst();
        int count = 0;
        for (final Element action : elements(detail)) {
            if (List.of("TypeId", "Description").contains(action.getLocalName())) {
                continue;
            }
            count += leaves(action);
        }
        return count;
    }

    private static int leaves(final Element element) {
        if ("Data".equals(element.getLocalName())) {
            return 0;
        }
        final List<Element> kids = elements(element);
        if (kids.isEmpty()) {
            return element.getTextContent().isBlank()
                    ? 0
                    : 1;
        }
        return kids.stream().mapToInt(TestE2eCatalogueTypedPlacements::leaves).sum();
    }

    // Every element child of an element.
    private static List<Element> elements(final Element parent) {
        final List<Element> out = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof final Element element) {
                out.add(element);
            }
        }
        return out;
    }

    // The draft with the device the schema needs, as the agent adds it: the client's address.
    private static Map<String, Object> withDevice(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final List<Map<String, Object>> common = new ArrayList<>(list(mapping, "common"));
        if (common.stream().noneMatch(entry -> String.valueOf(entry.get("path")).startsWith("EventSource/Device"))) {
            common.add(args("path", "EventSource/Device/IPAddress", "field", "ClientIP"));
        }
        mapping.put("common", common);
        return mapping;
    }
}
