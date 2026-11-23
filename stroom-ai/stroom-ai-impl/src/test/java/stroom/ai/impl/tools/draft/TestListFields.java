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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Fields whose values are lists, drafted as Data groups: which values are lists (and which aren't), the groups
/// written, and a long list costing little.
class TestListFields extends DraftFixtures {

    private static final List<String> CHANGES = List.of("""
            [{"Name": "DisplayName", "OldValue": "Sam T", "NewValue": "Sam Taylor"},
             {"Name": "JobTitle", "OldValue": "", "NewValue": "Analyst"}]""", """
            [{"Name": "DisplayName", "OldValue": "Kate", "NewValue": "Kate Jones"}]""", """
            [{"Name": "JobTitle", "OldValue": "Analyst", "NewValue": "Lead"},
             {"Name": "Department", "OldValue": "Ops", "NewValue": "Finance"}]""", """
            [{"Name": "DisplayName", "OldValue": "Al", "NewValue": "Al Smith"},
             {"Name": "Department", "OldValue": "", "NewValue": "Ops"}]""");

    // Graph-style targets, each with its old/new list of properties (values JSON text).
    private static final List<String> TARGETS = List.of("""
            [{"id": "u-1", "type": "User", "userPrincipalName": "sam@x", "modifiedProperties": [
              {"displayName": "DisplayName", "oldValue": "[\\"Sam T\\"]", "newValue": "[\\"Sam Taylor\\"]"}]},
             {"id": "g-1", "type": "Group", "modifiedProperties": [{"displayName": "Description",
              "oldValue": "[]", "newValue": "[\\"Ops team\\"]"}]}]""", """
            [{"id": "u-2", "type": "User", "userPrincipalName": "kate@x", "modifiedProperties": [
              {"displayName": "AccountEnabled", "oldValue": "[true]", "newValue": "[false]"},
              {"displayName": "DisplayName", "oldValue": "[\\"Kate\\"]", "newValue": "[\\"Kate Jones\\"]"}]}]""",
            """
            [{"id": "u-3", "type": "User", "userPrincipalName": "al@x", "modifiedProperties": [
              {"displayName": "AccountEnabled", "oldValue": "[false]", "newValue": "[true]"},
              {"displayName": "Description", "oldValue": "[]", "newValue": "[\\"Lead\\"]"}]}]""");

    private static ListFields.Split split(final String... values) {
        return ListFields.splitOf(List.of(values));
    }

    private static Map<String, Object> draft(final Object samples, final String source) {
        return DraftMapping.draft(samples, source, null, null, null, null, null);
    }

    // Every Data group (an entry with for_each) of a draft's mapping, common's and the rules'.
    private static List<Map<String, Object>> groups(final Map<String, Object> draft) {
        final List<Map<String, Object>> out = new ArrayList<>();
        JsonValues.maps(mappingOf(draft).get("common")).stream().filter(e -> e.containsKey("for_each"))
                .forEach(out::add);
        JsonValues.maps(mappingOf(draft).get("events")).forEach(rule -> JsonValues.maps(rule.get("fields")).stream()
                .filter(e -> e.containsKey("for_each")).forEach(out::add));
        return out;
    }

    // The draft's mapping with every rule's Unknown allowed and the system named, generated.
    @SuppressWarnings("unchecked")
    private static XsltGenerator.Generated generated(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(mappingOf(draft));
        final List<Object> events = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(rule);
            if (((List<Map<String, Object>>) copy.get("fields")).stream()
                    .anyMatch(f -> ((String) f.get("path")).contains("/Unknown/"))) {
                copy.put("allow_unknown", "kept unknown for the test");
            }
            events.add(copy);
        }
        mapping.put("events", events);
        return XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0");
    }

    @Test
    void listsOfAddressesAndIdsAreSplitOnTheirSeparator() {
        assertThat(split("a@example.org,b@example.org", "c@example.org", "d@example.org, e@example.org"))
                .isEqualTo(new ListFields.Split(",", ListFields.Kind.EMAIL));
        assertThat(split("a@example.org; b@example.org", "c@example.org;d@example.org;"))
                .isEqualTo(new ListFields.Split(";", ListFields.Kind.EMAIL));
        assertThat(split("10.0.0.1,10.0.0.2", "10.0.0.3"))
                .isEqualTo(new ListFields.Split(",", ListFields.Kind.IP_ADDRESS));
        assertThat(split("host1.example.org;web-02;db_3", "web-02"))
                .isEqualTo(new ListFields.Split(";", ListFields.Kind.NAME));
        assertThat(split("1001,1002,1003", "1004,1005"))
                .isEqualTo(new ListFields.Split(",", ListFields.Kind.NUMBER));
    }

    @Test
    void textNumbersAndMixedKindsAreNotLists() {
        // Free text with commas.
        assertThat(split("Backup done, all good", "Restore failed, retrying, please wait")).isNull();
        // Words parted by ", " are prose; parted by "," alone, a list of names.
        assertThat(split("Hi, there, all", "Hello, again, world")).isNull();
        assertThat(split("admin,dev,ops", "dev")).isEqualTo(new ListFields.Split(",", ListFields.Kind.NAME));
        // A person's name, last name first: two names alone are too weak to be a list.
        assertThat(split("Smith, John", "Chen, Riley")).isNull();
        // A number with thousands separators.
        assertThat(split("1,234,567", "12,345", "999")).isNull();
        // Pairs.
        assertThat(split("a=1,b=2", "c=3,d=4")).isNull();
        // Addresses and IP addresses mixed in one value.
        assertThat(split("a@example.org,10.0.0.1", "b@example.org,10.0.0.2")).isNull();
        // Single values only.
        assertThat(split("a@example.org", "b@example.org", "c@example.org")).isNull();
        // A list now and then: too few of the values.
        assertThat(split("a@example.org,b@example.org", "c@example.org", "d@example.org", "e@example.org",
                "f@example.org")).isNull();
        // Values of other kinds beside the lists.
        assertThat(split("a@example.org,b@example.org", "not an address", "nor this")).isNull();
        assertThat(split()).isNull();
    }

    @Test
    void aPieceTooLongToBeAnItemIsText() {
        assertThat(ListFields.pieces("x".repeat(600) + ",a", ",")).isNull();
        assertThat(ListFields.pieces(" a , ,b,", ",")).containsExactly("a", "b");
        assertThat(ListFields.pieces(IntStream.range(0, 200).mapToObj(i -> "u" + i).collect(Collectors.joining(";")),
                ";")).hasSize(ListFields.MAX_PIECES);
    }

    @Test
    void anItemIsNamedForItsList() {
        assertThat(ListFields.itemName("recipients")).isEqualTo("recipient");
        assertThat(ListFields.itemName("dst.addresses")).isEqualTo("address");
        assertThat(ListFields.itemName("entries")).isEqualTo("entry");
        assertThat(ListFields.itemName("to")).isEqualTo("item");
        assertThat(ListFields.itemName("status")).isEqualTo("item");
        assertThat(ListFields.itemName("class")).isEqualTo("item");
        assertThat(ListFields.itemName("@hosts")).isEqualTo("item");
    }

    @Test
    void anArraysObjectsKeysAreRead() {
        assertThat(ListFields.arrayKeys(List.of(
                "[{\"id\": \"a\", \"address\": {\"city\": \"x\"}}, {\"id\": \"b\", \"role\": \"r\"}]", "[]")))
                .containsExactly("id", "address.city", "role");
        // Scalars, and values that aren't arrays, aren't arrays of objects.
        assertThat(ListFields.arrayKeys(List.of("[\"a\", \"b\"]"))).isEmpty();
        assertThat(ListFields.arrayKeys(List.of("[{\"id\": \"a\"}]", "plain"))).isEmpty();
        assertThat(ListFields.arrayKeys(List.of("[]"))).isEmpty();
    }

    @Test
    void aLongListIsCheap() {
        // ~20,000 addresses, a few hundred KB, in each of a few values: only the first pieces are examined.
        final String longList = IntStream.range(0, 20_000)
                .mapToObj(n -> "user" + n + "@example.org")
                .collect(Collectors.joining(","));
        assertThat(longList.length()).isGreaterThan(300_000);
        final List<String> values = List.of(longList, longList, "solo@example.org");
        final Instant start = Instant.now();
        final ListFields.Split split = ListFields.splitOf(values);
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(1));
        assertThat(split).isEqualTo(new ListFields.Split(",", ListFields.Kind.EMAIL));
        // Text a few hundred KB long with no separator close by is given up on at once.
        final Instant textStart = Instant.now();
        assertThat(ListFields.splitOf(List.of("y".repeat(400_000) + ",z"))).isNull();
        assertThat(Duration.between(textStart, Instant.now())).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void aMailLogsRecipientsAreDraftedAsADataGroup() {
        final String text = IntStream.range(0, 4)
                .mapToObj(r -> "time=2026-11-10T09:00:0" + r + "Z host=mx01 from=alice@example.org recipients="
                               + (r % 2 == 0
                        ? "a" + r + "@example.org;b" + r + "@example.org"
                        : "c" + r + "@example.org") + " subject=\"Hi, there, all\" size=5")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = draft(Map.of("mail.log", text), "Mail");

        assertThat(groups(draft)).isNotEmpty().allSatisfy(group -> {
            assertThat(group).containsEntry("data_name", "recipients").containsEntry("as", "recipient")
                    .containsEntry("for_each", map("split", "recipients", "on", ";"))
                    .containsEntry("fields", List.of(map("path", "Data", "data_name", "recipient", "item", true)));
            assertThat((String) group.get("path")).endsWith("/Data");
        });
        // The subject, prose with commas, stays one Data.
        assertThat(String.valueOf(mappingOf(draft))).contains("data_name=subject, field=subject");
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note)
                .startsWith("'recipients' (mail addresses, split on ';') holds a list"));
        final XsltGenerator.Generated result = generated(draft);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
    }

    @Test
    void aJsonArrayOfObjectsIsDraftedAsADataGroup() {
        final String text = """
                [{"time": "2026-10-01T08:00:00Z", "action": "send", "host": "mx01", "sender": "jane",
                  "recipients": [{"id": "john", "address": {"domain": "mail.com"}, "roles": ["to", "cc"]},
                                 {"id": "jack", "status": "ok"}]},
                 {"time": "2026-10-01T08:05:00Z", "action": "send", "host": "mx01", "sender": "jane", "recipients": []},
                 {"time": "2026-10-01T08:07:00Z", "action": "send", "host": "mx01", "sender": "bob",
                  "recipients": [{"id": "jill"}]}]
                """;
        final Map<String, Object> draft = draft(Map.of("mail.json", text), "Mail");

        assertThat(groups(draft)).isNotEmpty().allSatisfy(group -> assertThat(group)
                .containsEntry("data_name", "recipients").containsEntry("as", "recipient")
                .containsEntry("for_each", map("array", "recipients"))
                .containsEntry("fields", List.of(
                        map("path", "Data", "data_name", "id", "field", "id"),
                        map("path", "Data", "data_name", "address.domain", "field", "address.domain"),
                        map("path", "Data", "data_name", "status", "field", "status"))));
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note)
                .startsWith("'recipients' (keys id, address.domain, status) holds an array of objects"));
        // A list inside each object is left out of the group, and said (seen in review: dropped unsaid).
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note)
                .startsWith("Not drafted: 'roles' in each object of 'recipients' (a list inside each object"));
        final XsltGenerator.Generated result = generated(draft);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
    }

    @Test
    void aNameValueListIsDraftedAsADataPerPairWithItsNamesNoted() {
        // Decided by the user (2026-11-23, from chat 187): a list of settings is Data, each named by its pair's
        // name, not a Data per object holding Data named Name and Value.
        final String text = """
                [{"time": "2026-10-01T08:00:00Z", "action": "rule", "host": "mx01", "user": "jo",
                  "Parameters": [{"Name": "Name", "Value": "FW to personal"}, {"Name": "From", "Value": "a@b.org"}]},
                 {"time": "2026-10-01T08:05:00Z", "action": "rule", "host": "mx01", "user": "al",
                  "Parameters": [{"Name": "Name", "Value": "Tidy"}, {"Name": "MoveToFolder", "Value": "Old"}]},
                 {"time": "2026-10-01T08:07:00Z", "action": "rule", "host": "mx01", "user": "bo",
                  "Parameters": [{"Name": "Name", "Value": "Junk"}, {"Name": "From", "Value": "c@d.org"}]}]
                """;
        final Map<String, Object> draft = draft(Map.of("rules.json", text), "Mail");

        assertThat(groups(draft)).isEmpty();
        assertThat(String.valueOf(mappingOf(draft)))
                .contains("pair={list=Parameters, name_key=Name, value_key=Value}");
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note)
                .startsWith("'Parameters' (names Name (3), From (2), MoveToFolder (1)) is a name/value list: "
                            + "drafted as a Data per pair, each named by its pair's name.")
                .contains("Never put a value in an element that means something else"));
        final XsltGenerator.Generated result = generated(draft);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        assertThat(result.xslt()).contains("<xsl:attribute name=\"Name\" select=\"string(*[@key='Name'])\"/>");
    }

    @Test
    void anArrayOfValuesIsADataPerValue() {
        // Decided by the user (2026-11-20, from eval case 69): Stroom's JSON as XML runs an array's values together
        // as its text, so a Data of the whole array reads "vpnoffice"; a Data per value, as the reference does.
        final String text = """
                [{"time": "2026-10-01T08:00:00Z", "host": "mx01", "action": "login", "user": "jo", \
                  "tags": ["vpn", "office"]},
                 {"time": "2026-10-01T08:05:00Z", "host": "mx01", "action": "login", "user": "al", "tags": ["vpn"]},
                 {"time": "2026-10-01T08:06:00Z", "host": "mx01", "action": "login", "user": "al", "tags": []}]
                """;
        final Map<String, Object> draft = draft(Map.of("a.json", text), "App");

        assertThat(groups(draft)).isEmpty();
        assertThat(String.valueOf(mappingOf(draft))).contains("data_name=tags, field=tags, repeat=true");
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note).startsWith("'tags' holds a list of values"));
        final XsltGenerator.Generated result = generated(draft);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
    }

    @Test
    void aFieldOfJsonValuesThatAreNotListsIsOneData() {
        // Text that only looks like a list, and arrays in too few values, stay one Data.
        assertThat(ListFields.valueArrays(List.of("[\"a\"]", "plain", "text", "more"))).isFalse();
        assertThat(ListFields.valueArrays(List.of("[\"a\", \"b\"]", "[1, 2]", "[]"))).isTrue();
        assertThat(ListFields.valueArrays(List.of("[{\"id\": 1}]"))).isFalse();
        assertThat(ListFields.valueArrays(List.of("[\"a\", [\"b\"]]"))).isFalse();
        assertThat(ListFields.valueArrays(List.of())).isFalse();
    }

    // A rule's mapping, its fields given, the common entries a translation needs.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ruleMapping(final String fields) {
        return (Map<String, Object>) ToolJson.loads("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                            {"path": "EventSource/System/Name", "value": "Directory"},
                            {"path": "EventSource/System/Environment", "value": "Dev"},
                            {"path": "EventSource/Generator", "value": "audit"},
                            {"path": "EventSource/Device/HostName", "value": "dir01"}],
                 "events": [{"name": "update", "fields": [{"path": "EventDetail/TypeId", "value": "Update-User"},
                             %s]}]}""".formatted(fields));
    }

    private static List<Map<String, Object>> fieldsOf(final Map<String, Object> mapping) {
        return JsonValues.maps(JsonValues.maps(mapping.get("events")).getFirst().get("fields"));
    }

    @Test
    void anOldNewListUnderAnUpdateIsDraftedAsSnapshotsOfTheObjectTheRuleWrites() {
        // Decided by the user (2026-11-23): Before/After snapshots of the changed object, each property Data of its
        // side's value; the object the one the rule's other entries write in a snapshot (an affected user).
        final Map<String, Object> mapping = ruleMapping("""
                {"path": "EventDetail/Update/After/User/Id", "field": "target"},
                {"path": "EventDetail/Update/Data", "data_name": "ModifiedProperties",
                 "field": "ModifiedProperties"}""");

        final List<String> notes = ListFields.propose(mapping, Map.of("ModifiedProperties", CHANGES), true);

        final List<Map<String, Object>> fields = fieldsOf(mapping);
        assertThat(fields).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/After/User/Id", "EventDetail/Update/Before/User/Data",
                "EventDetail/Update/After/User/Data");
        assertThat(String.valueOf(fields.get(2).get("change")))
                .isEqualTo("{list=ModifiedProperties, name_key=Name, old_key=OldValue, new_key=NewValue}");
        assertThat(notes).anySatisfy(note -> assertThat(note)
                .startsWith("'ModifiedProperties' (properties DisplayName (3), JobTitle (2), Department (2); named "
                            + "by Name, old values in OldValue, new in NewValue) is an old/new list")
                .contains("swap old_key and new_key")
                .contains("\"change\": {\"list\": \"ModifiedProperties\""));
        assertThat(XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0").ok()).isTrue();
    }

    @Test
    void theSnapshotsObjectIsObjectWhereNothingBetterIsKnownAndOtherElementsKeepAnArrayGroup() {
        final Map<String, Object> mapping = ruleMapping("""
                {"path": "EventDetail/Update/Data", "data_name": "ModifiedProperties",
                 "field": "ModifiedProperties"}""");

        ListFields.propose(mapping, Map.of("ModifiedProperties", CHANGES), true);

        assertThat(fieldsOf(mapping)).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/Before/Object/Data", "EventDetail/Update/After/Object/Data");

        // An Authorise has no snapshots: its list is an array of objects as before.
        final Map<String, Object> other = ruleMapping("""
                {"path": "EventDetail/Authorise/Data", "data_name": "ModifiedProperties",
                 "field": "ModifiedProperties"}""");
        ListFields.propose(other, Map.of("ModifiedProperties", CHANGES), true);
        assertThat(fieldsOf(other).get(1)).containsEntry("path", "EventDetail/Authorise/Data")
                .containsKey("for_each");
    }

    @Test
    void targetsPlacedAsAGroupInAfterAloneAreWrittenInBothSnapshotsWithADataPerProperty() {
        // Seen in eval case 76 (Haiku): the catalogue's targets as a group in After alone, its one field the id,
        // never the change form: no property's old or new value was written.
        final Map<String, Object> mapping = ruleMapping("""
                {"path": "EventDetail/Update/After/Object", "for_each": {"array": "targetResources"},
                 "as": "targetResource", "fields": [{"path": "Id", "field": "id", "item": true}]}""");

        final List<String> notes = ListFields.propose(mapping, Map.of("targetResources", TARGETS), true);

        final List<Map<String, Object>> fields = fieldsOf(mapping);
        assertThat(fields).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/Before/Object", "EventDetail/Update/After/Object");
        for (final Map<String, Object> group : fields.subList(1, 3)) {
            assertThat(JsonValues.maps(group.get("fields"))).extracting(Object::toString).containsExactly(
                    "{path=Id, field=id, item=true}",
                    "{path=Data, change={list=modifiedProperties, name_key=displayName, old_key=oldValue, "
                    + "new_key=newValue}}");
        }
        assertThat(notes).anySatisfy(note -> assertThat(note).startsWith(ListFields.SNAPSHOTS_WRITTEN)
                .contains("each item of 'targetResources' written in both snapshots, "
                          + "EventDetail/Update/Before/Object too (its old values there) (update)"));
        assertThat(XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0").ok()).isTrue();

        // Drafted again from its own output, it is as it was: nothing added twice.
        final Map<String, Object> once = JsonValues.deepCopy(mapping);
        assertThat(ListFields.propose(mapping, Map.of("targetResources", TARGETS), true))
                .noneSatisfy(note -> assertThat(note).startsWith(ListFields.SNAPSHOTS_WRITTEN));
        assertThat(mapping).isEqualTo(once);
    }

    @Test
    void targetsPlacedInEachSnapshotKeepTheirOwnFieldsEachWithADataPerProperty() {
        // Seen in eval case 76 (Haiku, then Sonnet's shape without the change form): each snapshot's group placed
        // by the model, the user's principal name as Name.
        final String group = """
                {"path": "EventDetail/Update/%s/User", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}%s]}""";
        final Map<String, Object> mapping = ruleMapping(group.formatted("Before", "") + ", "
                                                        + group.formatted("After",
                ", {\"path\": \"Name\", \"field\": \"userPrincipalName\"}"));

        ListFields.propose(mapping, Map.of("targetResources", TARGETS), true);

        final List<Map<String, Object>> fields = fieldsOf(mapping);
        assertThat(fields).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/Before/User", "EventDetail/Update/After/User");
        assertThat(JsonValues.maps(fields.get(1).get("fields"))).extracting(field -> field.get("path"))
                .containsExactly("Id", "Data");
        assertThat(JsonValues.maps(fields.get(2).get("fields"))).extracting(field -> field.get("path"))
                .containsExactly("Id", "Name", "Data");
    }

    @Test
    void anOldNewListInsideTargetsIsNotOfferedAsAGroupOfItsOwn() {
        // Seen in eval case 76: the draft's note offered a for_each group over modifiedProperties (each target's
        // old/new list), which the catalogue refuses, beside the old/new list's own note.
        final Map<String, Object> mapping = ruleMapping("""
                {"path": "EventDetail/Unknown/Data", "data_name": "targetResources", "field": "targetResources"}""");

        final List<String> notes = ListFields.propose(mapping, Map.of("targetResources", TARGETS), true);

        assertThat(notes).noneSatisfy(note -> assertThat(note).startsWith("Not drafted"));
    }

    @Test
    void targetsEachHoldingAnOldNewListAreAnObjectEachInBothSnapshots() {
        final List<String> targets = List.of("""
                [{"id": "u-1", "type": "User", "modifiedProperties": [{"displayName": "DisplayName",
                  "oldValue": "[\\"Sam T\\"]", "newValue": "[\\"Sam Taylor\\"]"}]},
                 {"id": "g-1", "type": "Group", "modifiedProperties": [{"displayName": "Description",
                  "oldValue": "[]", "newValue": "[\\"Ops team\\"]"}]}]""", """
                [{"id": "u-2", "type": "User", "modifiedProperties": [{"displayName": "AccountEnabled",
                  "oldValue": "[true]", "newValue": "[false]"}, {"displayName": "DisplayName",
                  "oldValue": "[\\"Kate\\"]", "newValue": "[\\"Kate Jones\\"]"}]}]""", """
                [{"id": "u-3", "type": "User", "modifiedProperties": [{"displayName": "AccountEnabled",
                  "oldValue": "[false]", "newValue": "[true]"}, {"displayName": "Description",
                  "oldValue": "[]", "newValue": "[\\"Lead\\"]"}]}]""");
        final Map<String, Object> mapping = ruleMapping("""
                {"path": "EventDetail/Update/Data", "data_name": "targetResources", "field": "targetResources"}""");

        final List<String> notes = ListFields.propose(mapping, Map.of("targetResources", targets), true);

        final List<Map<String, Object>> fields = fieldsOf(mapping);
        assertThat(fields).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/Before/Object", "EventDetail/Update/After/Object");
        assertThat(String.valueOf(fields.get(1).get("fields"))).contains("data_name=id, field=id")
                .contains("change={list=modifiedProperties, name_key=displayName, old_key=oldValue, "
                          + "new_key=newValue}");
        assertThat(notes).anySatisfy(note -> assertThat(note)
                .startsWith("'modifiedProperties' in each item of 'targetResources' (properties")
                .contains("each item's type is one of User, Group"));
        assertThat(notes).noneSatisfy(note -> assertThat(note).startsWith("Not drafted"));
        assertThat(XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0").ok()).isTrue();
    }
}
