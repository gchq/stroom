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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.sample.NameValueLists.NameValueList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestNameValueLists {

    private static final String SAMPLES = "stroom-app/src/test/resources/stroom/mcp/impl/e2e/aggregated/";
    private static final String AZURE = "azure-audit/";
    private static final String ENTRA = "entra-audit/";

    @ParameterizedTest
    @ValueSource(strings = {"azure-audit-1.json", "azure-audit-2.json"})
    void theSettingsListsOfAnAuditSampleAreNameValueListsAndItsEntityListsAreNot(final String file) {
        final Map<String, List<Object>> lists = arrays(AZURE + file);

        for (final String field : List.of("event.OperationProperties", "event.Parameters",
                "event.ExtendedProperties", "event.DeviceProperties")) {
            assertThat(NameValueLists.detect(lists.get(field)))
                    .as(field)
                    .hasValueSatisfying(found -> {
                        assertThat(found.nameKey()).isEqualTo("Name");
                        assertThat(found.valueKey()).isEqualTo("Value");
                    });
        }
        // Entities with a type code, users, and things: not name/value lists.
        for (final String field : List.of("event.Actor", "event.Target", "event.Body.Mentions",
                "event.Folders.FolderItems")) {
            assertThat(lists).containsKey(field);
            assertThat(NameValueLists.detect(lists.get(field))).as(field).isEmpty();
        }
    }

    @Test
    void theNamesAreCountedMostFirst() {
        final List<Object> values = List.of(
                "[{\"Name\": \"Name\", \"Value\": \"FW to personal\"}, {\"Name\": \"From\", \"Value\": \"a@b\"}]",
                "[{\"Name\": \"Name\", \"Value\": \"Tidy\"}, {\"Name\": \"MoveToFolder\", \"Value\": \"Old\"}]",
                "[{\"Name\": \"Name\", \"Value\": \"Junk\"}, {\"Name\": \"From\", \"Value\": \"c@d\"}]");

        final Optional<NameValueList> found = NameValueLists.detect(values);

        assertThat(found).isPresent();
        assertThat(found.get().names()).containsExactly(Map.entry("Name", 3), Map.entry("From", 2),
                Map.entry("MoveToFolder", 1));
        assertThat(NameValueLists.said(found.get().names(), 2)).isEqualTo("Name (3), From (2), … 1 more");
    }

    @Test
    void theNameIsTheKeyWithFewerValuesWhicheverComesFirst() {
        final List<Object> values = List.of(
                List.of(Map.of("v", "Bind", "k", "MailAccessType"), Map.of("v", "False", "k", "IsThrottled")),
                List.of(Map.of("v", "Sync", "k", "MailAccessType"), Map.of("v", "True", "k", "IsThrottled")));

        assertThat(NameValueLists.detect(values)).hasValueSatisfying(found -> {
            assertThat(found.nameKey()).isEqualTo("k");
            assertThat(found.valueKey()).isEqualTo("v");
        });
    }

    @Test
    void aListWhoseItemsHoldTheirValuesUnderKeysOfTheirOwnIsOne() {
        // As a reports API writes its parameters: the value's key says its kind.
        final List<Object> values = List.of("""
                [{"name": "USER_EMAIL", "value": "a@x.org"}, {"name": "IS_ADMIN", "boolValue": true},
                 {"name": "COUNT", "intValue": "3"}, {"name": "GROUPS", "multiValue": ["a", "b"]}]""", """
                [{"name": "USER_EMAIL", "value": "b@x.org"}, {"name": "IS_ADMIN", "boolValue": false},
                 {"name": "GROUPS", "multiValue": ["c"]}]""", """
                [{"name": "USER_EMAIL", "value": "c@x.org"}, {"name": "IS_ADMIN", "boolValue": true}]""");

        final Optional<NameValueList> found = NameValueLists.detect(values);

        assertThat(found).hasValueSatisfying(list -> {
            assertThat(list.nameKey()).isEqualTo("name");
            assertThat(list.valueKey()).isEqualTo(NameValueLists.ANY_VALUE_KEY);
            assertThat(list.names()).containsKeys("USER_EMAIL", "IS_ADMIN", "COUNT", "GROUPS");
        });
        assertThat(NameValueLists.valueOf(Map.of("name", "GROUPS", "multiValue", List.of("a", "b")), "name",
                NameValueLists.ANY_VALUE_KEY)).isEqualTo("a, b");
        assertThat(NameValueLists.valueOf(Map.of("name", "IS_ADMIN", "boolValue", true), "name",
                NameValueLists.ANY_VALUE_KEY)).isEqualTo("true");
    }

    @Test
    void aNameWithItsOldAndNewValuesIsntANameValueList() {
        assertThat(NameValueLists.detect(List.of("""
                [{"Name": "Title", "OldValue": "a", "NewValue": "b"}]""", """
                [{"Name": "Title", "OldValue": "b", "NewValue": "c"}, {"Name": "Dept", "OldValue": "x",
                  "NewValue": "y"}]"""))).isEmpty();
    }

    @Test
    void itemsSharingNoKeyOrHoldingObjectsArentNameValueLists() {
        assertThat(NameValueLists.detect(List.of("""
                [{"a": "x", "b": "y"}, {"c": "x", "d": "y"}]""", """
                [{"a": "x", "b": "y"}]"""))).isEmpty();
        assertThat(NameValueLists.detect(List.of("""
                [{"name": "Owner", "objectValue": {"id": 1}}]""", """
                [{"name": "Owner", "objectValue": {"id": 2}}]"""))).isEmpty();
    }

    @Test
    void aPairsListIsReadByItsNameWhereOneListCantBeToldByShape() {
        final List<Object> one = List.of(
                "[{\"Name\": \"OS\", \"Value\": \"Ios\"}, {\"Name\": \"BrowserType\", \"Value\": \"Safari\"}]");

        assertThat(NameValueLists.forPair(one, "OS")).hasValueSatisfying(list -> {
            assertThat(list.nameKey()).isEqualTo("Name");
            assertThat(list.valueKey()).isEqualTo("Value");
        });
        assertThat(NameValueLists.forPair(one, "Colour")).isEmpty();
        assertThat(NameValueLists.forPair(List.of("[{\"UPN\": \"al\", \"Name\": \"Al\", \"Role\": \"x\"}]"),
                "Al")).isEmpty();
    }

    @Test
    void oneListIsntEnoughToTell() {
        assertThat(NameValueLists.detect(List.of(
                "[{\"Name\": \"OS\", \"Value\": \"Ios\"}, {\"Name\": \"BrowserType\", \"Value\": \"Safari\"}]")))
                .isEmpty();
    }

    @Test
    void aNameTwiceInOneListIsntAPairPerName() {
        assertThat(NameValueLists.detect(List.of(
                "[{\"Name\": \"Tag\", \"Value\": \"a\"}, {\"Name\": \"Tag\", \"Value\": \"b\"}]",
                "[{\"Name\": \"Tag\", \"Value\": \"c\"}, {\"Name\": \"Other\", \"Value\": \"d\"}]")))
                .isEmpty();
    }

    @Test
    void itemsOfOtherShapesArentNameValueLists() {
        // Three keys; a number; a value that isn't a list; an item that isn't an object.
        assertThat(NameValueLists.detect(List.of(
                "[{\"Name\": \"OS\", \"Value\": \"Ios\", \"Extra\": \"x\"}]",
                "[{\"Name\": \"OS\", \"Value\": \"Ios\", \"Extra\": \"y\"}]"))).isEmpty();
        assertThat(NameValueLists.detect(List.of(
                "[{\"Name\": \"Count\", \"Value\": 3}]", "[{\"Name\": \"Count\", \"Value\": 4}]"))).isEmpty();
        assertThat(NameValueLists.detect(List.of("plain text", "[{\"Name\": \"OS\", \"Value\": \"Ios\"}]")))
                .isEmpty();
        assertThat(NameValueLists.detect(List.of("[\"OS\", \"Ios\"]", "[\"OS\", \"Android\"]"))).isEmpty();
        assertThat(NameValueLists.detect(List.of())).isEmpty();
    }

    @Test
    void shortCodesArentNames() {
        assertThat(NameValueLists.detect(List.of(
                "[{\"T\": \"U\", \"Id\": \"a1\"}, {\"T\": \"G\", \"Id\": \"b2\"}]",
                "[{\"T\": \"U\", \"Id\": \"c3\"}, {\"T\": \"G\", \"Id\": \"d4\"}]"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"azure-audit-1.json", "azure-audit-2.json"})
    void anAuditSamplesModifiedPropertiesAreAnOldNewListAndItsOtherListsAreNot(final String file) {
        final Map<String, List<Object>> lists = arrays(AZURE + file);

        // The sample's items are {Name, NewValue, OldValue}: by the items' key order, NewValue is taken as old (the
        // keys are never read by name; a placement names them where the order says otherwise).
        assertThat(NameValueLists.changes(lists.get("event.ModifiedProperties"))).hasValueSatisfying(found -> {
            assertThat(found.nameKey()).isEqualTo("Name");
            assertThat(found.oldKey()).isEqualTo("NewValue");
            assertThat(found.newKey()).isEqualTo("OldValue");
            assertThat(found.names()).containsKeys("Role.DisplayName", "Role.TemplateId",
                    "ForceChangePasswordNextLogin");
        });
        for (final String field : List.of("event.OperationProperties", "event.Parameters", "event.Actor",
                "event.Target", "event.Body.Mentions", "event.Folders.FolderItems", "event.Members",
                "event.Attendees", "event.AffectedItems", "event.Item.AttachmentDetails")) {
            if (lists.containsKey(field)) {
                assertThat(NameValueLists.changes(lists.get(field))).as(field).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"entra-audit-1.json", "entra-audit-2.json"})
    void aDirectoryAuditSamplesTargetsHoldOldNewListsAndItsDetailsAreANameValueList(final String file) {
        final Map<String, List<Object>> lists = arrays(ENTRA + file);

        assertThat(NameValueLists.changes(lists.get("targetResources.modifiedProperties")))
                .hasValueSatisfying(found -> {
                    assertThat(found.nameKey()).isEqualTo("displayName");
                    assertThat(found.oldKey()).isEqualTo("oldValue");
                    assertThat(found.newKey()).isEqualTo("newValue");
                    assertThat(found.names()).containsKeys("DisplayName", "JobTitle", "AccountEnabled",
                            "Group.DisplayName", "Description", "Included Updated Properties");
                });
        assertThat(NameValueLists.detect(lists.get("additionalDetails"))).hasValueSatisfying(found -> {
            assertThat(found.nameKey()).isEqualTo("key");
            assertThat(found.valueKey()).isEqualTo("value");
        });
        // The targets are things, each holding a list: neither kind of list.
        assertThat(NameValueLists.changes(lists.get("targetResources"))).isEmpty();
        assertThat(NameValueLists.detect(lists.get("targetResources"))).isEmpty();
        assertThat(NameValueLists.changes(lists.get("additionalDetails"))).isEmpty();
    }

    @Test
    void graphStyleChangesHoldingJsonTextAreAnOldNewListInTheItemsOrder() {
        final List<Object> values = List.of("""
                [{"displayName": "DisplayName", "oldValue": "[\\"Sam T\\"]", "newValue": "[\\"Sam Taylor\\"]"},
                 {"displayName": "AccountEnabled", "oldValue": "[true]", "newValue": "[false]"}]""", """
                [{"displayName": "AccountEnabled", "oldValue": "[false]", "newValue": "[true]"}]""", """
                [{"displayName": "JobTitle", "oldValue": "[]", "newValue": "[\\"Analyst\\"]"}]""", """
                [{"displayName": "AccountEnabled", "oldValue": "[true]", "newValue": "[false]"},
                 {"displayName": "JobTitle", "oldValue": "[\\"Analyst\\"]", "newValue": "[\\"Lead\\"]"}]""");

        assertThat(NameValueLists.changes(values)).hasValueSatisfying(found -> {
            assertThat(found.nameKey()).isEqualTo("displayName");
            assertThat(found.oldKey()).isEqualTo("oldValue");
            assertThat(found.newKey()).isEqualTo("newValue");
            assertThat(found.names()).containsExactly(Map.entry("AccountEnabled", 3), Map.entry("JobTitle", 2),
                    Map.entry("DisplayName", 1));
        });
        // A name/value list it is not, nor the other way round.
        assertThat(NameValueLists.detect(values)).isEmpty();
        assertThat(NameValueLists.changes(List.of(
                "[{\"Name\": \"OS\", \"Value\": \"Ios\"}, {\"Name\": \"Browser\", \"Value\": \"Edge\"}]",
                "[{\"Name\": \"OS\", \"Value\": \"Mac\"}]"))).isEmpty();
    }

    @Test
    void entitiesOfThreeKeysArentOldNewLists() {
        // One entity a list, its type unique in it and recurring: its id and its name are one to one.
        assertThat(NameValueLists.changes(List.of("""
                [{"Type": "User", "ID": "u-1", "Name": "sam"}]""", """
                [{"Type": "User", "ID": "u-2", "Name": "kate"}]""", """
                [{"Type": "Group", "ID": "g-1", "Name": "ops"}]""", """
                [{"Type": "User", "ID": "u-1", "Name": "sam"}]"""))).isEmpty();
        // A name now and then missing: an id and a display name aren't of one shape.
        assertThat(NameValueLists.changes(List.of("""
                [{"type": "User", "id": "5e3c1f0a-1b2c-4d5e-8f90-a1b2c3d4e5f6", "displayName": "Sam Taylor"}]""", """
                [{"type": "User", "id": "0b9a8c7d-6e5f-4a3b-9c2d-1e0f2a3b4c5d", "displayName": null}]""", """
                [{"type": "Group", "id": "6f1e2d3c-0a1b-4c2d-8e3f-4a5b6c7d8e9f", "displayName": "Ops Team"}]""",
                """
                [{"type": "User", "id": "1a2b3c4d-0000-4c2d-8e3f-4a5b6c7d8e9f", "displayName": "Kate Jones"}]""")))
                .isEmpty();
        // A name twice in a list, a key holding an object, items of other keys, one list: none is one.
        assertThat(NameValueLists.changes(List.of("""
                [{"Name": "Tag", "Old": "a", "New": "b"}, {"Name": "Tag", "Old": "c", "New": "d"}]""", """
                [{"Name": "Tag", "Old": "e", "New": "f"}]"""))).isEmpty();
        assertThat(NameValueLists.changes(List.of("""
                [{"Name": "Owner", "Old": {"id": 1}, "New": {"id": 2}}]""", """
                [{"Name": "Owner", "Old": {"id": 2}, "New": {"id": 3}}]"""))).isEmpty();
        assertThat(NameValueLists.changes(List.of("""
                [{"Name": "Title", "Old": "a", "New": "b"}]""", """
                [{"Name": "Title", "Was": "b", "Now": "c"}]"""))).isEmpty();
        assertThat(NameValueLists.changes(List.of("""
                [{"Name": "Title", "Old": "a", "New": "b"}, {"Name": "Dept", "Old": "x", "New": "y"}]""")))
                .isEmpty();
    }

    @Test
    void anOldOrNewValueHeldAsJsonTextIsUnwrappedToOneValue() {
        assertThat(NameValueLists.changeText("[\"true\"]")).isEqualTo("true");
        assertThat(NameValueLists.changeText("[true]")).isEqualTo("true");
        assertThat(NameValueLists.changeText("\"Sam T\"")).isEqualTo("Sam T");
        assertThat(NameValueLists.changeText("[]")).isEmpty();
        assertThat(NameValueLists.changeText(null)).isEmpty();
        // Longer arrays and objects stay as their JSON text; a list as read is joined, as a pair's is.
        assertThat(NameValueLists.changeText("[\"E3\",\"E5\"]")).isEqualTo("[\"E3\",\"E5\"]");
        assertThat(NameValueLists.changeText("{\"a\": 1}")).isEqualTo("{\"a\": 1}");
        assertThat(NameValueLists.changeText(List.of("E3", "E5"))).isEqualTo("E3, E5");
        assertThat(NameValueLists.changeText("plain")).isEqualTo("plain");
    }

    @Test
    void theListsInsideAnArraysItemsAreReadItemByItem() {
        final List<Object> held = NameValueLists.heldIn(List.of("""
                [{"id": "a", "props": [{"n": "x"}]}, {"id": "b"}]""", """
                [{"id": "c", "props": [{"n": "y"}]}]"""), "props");

        assertThat(held).hasSize(2);
    }

    // Each array of objects in the sample's records, by its dotted path (an array inside an array's items by the
    // items' path), each record's (or item's) array a value.
    private static Map<String, List<Object>> arrays(final String file) {
        final Map<String, List<Object>> out = new LinkedHashMap<>();
        try {
            for (final String line : Files.readAllLines(samples().resolve(file))) {
                if (!line.isBlank()) {
                    collect(ToolJson.loads(line), "", out);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static void collect(final Object value, final String path, final Map<String, List<Object>> out) {
        if (value instanceof final Map<?, ?> map) {
            map.forEach((key, inner) -> collect(inner, path.isEmpty()
                    ? String.valueOf(key)
                    : path + "." + key, out));
        } else if (value instanceof final List<?> list && !list.isEmpty() && list.getFirst() instanceof Map) {
            out.computeIfAbsent(path, ignored -> new ArrayList<>()).add(list);
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    map.forEach((key, inner) -> {
                        if (inner instanceof List) {
                            collect(inner, path + "." + key, out);
                        }
                    });
                }
            }
        }
    }

    // The samples' folder, found from the module's folder up.
    private static Path samples() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.isDirectory(at.resolve(SAMPLES))) {
            at = at.getParent();
        }
        assertThat(at).as("the repository holding " + SAMPLES).isNotNull();
        return at.resolve(SAMPLES);
    }
}
