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
                  "recipients": [{"id": "john", "address": {"domain": "mail.com"}}, {"id": "jack", "status": "ok"}]},
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
        final XsltGenerator.Generated result = generated(draft);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
    }

    @Test
    void anArrayOfValuesIsLeftAsItWas() {
        final String text = """
                [{"time": "2026-10-01T08:00:00Z", "action": "login", "tags": ["vpn", "office"]},
                 {"time": "2026-10-01T08:05:00Z", "action": "login", "tags": ["vpn"]}]
                """;
        final Map<String, Object> draft = draft(Map.of("a.json", text), "App");

        assertThat(groups(draft)).isEmpty();
        assertThat(String.valueOf(mappingOf(draft))).contains("data_name=tags, field=tags");
    }
}
