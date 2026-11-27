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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.ToolOutputs;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.CouldType;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Typing;
import stroom.ai.impl.tools.catalogue.CatalogueValidation.Problem;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestCatalogueCheckResult {

    // The schema's homes in EventSource every event type had open in chats 184 to 186, and the Outcome's.
    private static final List<String> SHARED_HOMES = List.of(
            "Outcome: Success, Permitted, Description, AuthService/Id",
            "EventSource/Client: Id, Name, HostName, MACAddress, Port",
            "EventSource/User: Type, Name, Description, State, Domain, EmailAddress",
            "EventSource/{Device,Server}: Id, Name, HostName, IPAddress, MACAddress, Port",
            "EventSource/Door: Name, Description, SingleEntry, RemoveAll",
            "EventSource: EventId, SessionId, Interactive");
    private static final List<String> ACTIONS = List.of("View", "Send", "Move", "Update", "Send", "Authorise",
            "View");
    private static final String PATH = "/outputs/turn-1/call-2.txt";

    @Test
    void theSchemaHomesEveryEventTypeHasOpenAreSaidOnce() {
        final CatalogueCheckResult check = sevenEventTypes();

        final Map<String, Object> json = check.toJson();

        final Map<String, Object> all = JsonValues.mapOrEmpty(json.get(CatalogueCheckResult.COULD_TYPE_ALL));
        assertThat(String.valueOf(all.get("schema"))).isEqualTo(String.join("; ", SHARED_HOMES));
        final Map<String, Object> typing = JsonValues.mapOrEmpty(json.get("typing"));
        assertThat(typing).hasSize(7);
        typing.values().forEach(one -> {
            final String schema = String.valueOf(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(one)
                    .get("could_type")).get("schema"));
            assertThat(schema).isNotBlank().doesNotContain("EventSource", "Outcome: ");
        });
        // The listing saved whole keeps each event type's homes as they are.
        final Map<String, Object> full = check.toFullJson();
        assertThat(full).doesNotContainKey(CatalogueCheckResult.COULD_TYPE_ALL);
        assertThat(String.valueOf(full.get("typing"))).contains("EventSource/Door: Name");
    }

    @Test
    void oneEventTypeKeepsItsHomesInItsOwnCouldType() {
        final Map<String, Typing> typing = new LinkedHashMap<>();
        typing.put("View-MailItems", typing(0));
        final CatalogueCheckResult check = new CatalogueCheckResult(Map.of(), List.of(),
                CatalogueCheckResult.Uncatalogued.NONE, CatalogueCheckResult.Unparsed.NONE, List.of(), List.of(),
                List.of(), List.of("View-MailItems"), List.of(), typing);

        final Map<String, Object> json = check.toJson();

        assertThat(json).doesNotContainKey(CatalogueCheckResult.COULD_TYPE_ALL);
        assertThat(String.valueOf(json.get("typing"))).contains("EventSource/Door: Name");
    }

    @Test
    void theChatsStubOfACheckCarriesTheTypingOfSevenEventTypesWhole() {
        // Seen in chats 184 to 186: each of 7 event types' typing some 2,000 characters, so only the first two fit
        // the 4,000 a part had, and check.typing was read back with jq 29 times.
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "checked");
        result.put("recorded", false);
        result.put("check", sevenEventTypes().toJson());
        result.put("hint", "Nothing recorded: the proposals bring problems (check.problems).");

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false)), PATH,
                null);

        final String actOn = stub.substring(stub.indexOf("What it says to act on"), stub.indexOf("It starts with"));
        assertThat(actOn)
                .contains("check.problems:\n- {")
                .contains("check.unplaced:\n- {")
                .contains("check.could_type_all:\n- schema: Outcome: Success")
                .doesNotContain("query `.check.typing")
                .doesNotContain("not shown here for room");
        for (int i = 0; i < ACTIONS.size(); i++) {
            assertThat(actOn).contains("\n- " + typeId(i) + ": {\"typed\":0");
        }
        // The shared homes said once.
        assertThat(actOn.split("EventSource/Door: Name", -1)).hasSize(2);
    }

    // A check of 7 event types, each keeping most of its fields as Data, with the problems and unplaced elements
    // the check of chat 185 found.
    private static CatalogueCheckResult sevenEventTypes() {
        final Map<String, Typing> typing = new LinkedHashMap<>();
        final List<Problem> problems = new ArrayList<>();
        final List<Problem> unplaced = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        for (int i = 0; i < ACTIONS.size(); i++) {
            final String typeId = typeId(i);
            order.add(typeId);
            typing.put(typeId, typing(i));
            problems.add(new Problem(typeId, 0, "condition on Workload, the build's source type field: the source "
                                                + "type group's module passes other source types over already, so "
                                                + "tell the event types apart by their records' other fields or "
                                                + "shape"));
            problems.add(new Problem(typeId, -1, "event type " + typeId + ": EventDetail/" + ACTIONS.get(i)
                                                 + " has no Action, so leave action out"));
            unplaced.add(new Problem(typeId, -1, "event type " + typeId + ": EventDetail/" + ACTIONS.get(i)
                                                 + "/Source is required by the schema, and nothing the catalogue "
                                                 + "gives is written there: give a placement (e.g. {path: "
                                                 + "'Source/User/Id', field: <the field that holds it>}) from what "
                                                 + "the records hold"));
        }
        return new CatalogueCheckResult(Map.of(), List.of(), CatalogueCheckResult.Uncatalogued.NONE,
                CatalogueCheckResult.Unparsed.NONE, List.of(), problems, List.of(), order, unplaced, typing);
    }

    private static String typeId(final int i) {
        return ACTIONS.get(i) + "-MailItems" + i;
    }

    // An event type's typing as chat 185's were: 12 Data fields listed with examples, its own homes, the shared
    // ones, and its arrays of objects.
    private static Typing typing(final int i) {
        final List<String> data = new ArrayList<>();
        for (int f = 0; f < 12; f++) {
            data.add("event.Item.Field" + i + "x" + f + " (214/214, e.g. 'AAMk932852733B0B4EB124287576D0…')");
        }
        data.add("… 14 more");
        final List<String> homes = new ArrayList<>();
        // Each event type's own: what its action element holds (as chat 185's View and Send types' did).
        if ("View".equals(ACTIONS.get(i))) {
            homes.add("other objects: Association, Banner, Chat, Configuration, Criteria, Document, Email, File, "
                      + "Folder, Group, GroupChat, Object, SearchResults, Shortcut, … 4 more");
        } else {
            homes.add("Source: User/Id, Device/Id; Destination: User/Id, Device/Id");
            homes.add("Payload: Association/Id, Banner/Id, Chat/Id, Configuration/Id, Criteria/Id, Document/Id, "
                      + "Email/Id, File/Id, … 4 more");
        }
        homes.addAll(SHARED_HOMES);
        final List<String> arrays = List.of("event.Item.AttachmentDetails: items with Name, ContentType, "
                                            + "SizeInBytes — a for_each group on an element the schema repeats "
                                            + "types each");
        return new Typing(0, 26, Map.of(), "most of its fields are kept as Data (26 of 26; typed: 0): place each "
                                           + "whose meaning an element carries at the element that means it",
                new CouldType(data, homes, arrays));
    }
}
