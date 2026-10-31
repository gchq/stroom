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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.FieldMapping;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// What every Field mapping section is made of: grouping, paths, sources and conditions in words, the schema's
/// words, field descriptions and those kept with the documentation.
class TestFieldDocs {

    @Test
    void groupsWithRoughlyTheSameFieldsAreShownTogether() {
        // Asked for by the user: Send and Receive, say, under one heading; a path's action element counts as any.
        final Map<String, Set<String>> signatures = new LinkedHashMap<>();
        signatures.put("Send", Set.of("EventDetail/*/Source/User/Id", "EventDetail/*/Destination/User/Id"));
        signatures.put("View", Set.of("EventDetail/*/Document/Title"));
        signatures.put("Receive", Set.of("EventDetail/*/Source/User/Id", "EventDetail/*/Destination/User/Id"));
        assertThat(FieldDocs.similarGroups(signatures)).containsExactly(List.of("Send", "Receive"), List.of("View"));
        assertThat(FieldDocs.combinedPath(List.of("EventDetail/Send/Source/User/Id",
                "EventDetail/Receive/Source/User/Id"))).isEqualTo("EventDetail/(Send|Receive)/Source/User/Id");
        assertThat(FieldDocs.combinedPath(List.of("EventDetail/TypeId", "EventDetail/TypeId")))
                .isEqualTo("EventDetail/TypeId");
        assertThat(FieldDocs.combinedPath(List.of("EventSource/User/Id", "EventDetail/View/User/Id")))
                .isEqualTo("EventSource/User/Id\nEventDetail/View/User/Id");
    }

    @Test
    void aGroupWithNoFieldsStandsAlone() {
        final Map<String, Set<String>> signatures = new LinkedHashMap<>();
        signatures.put("A", Set.of());
        signatures.put("B", Set.of());
        signatures.put("C", new LinkedHashSet<>(List.of("x", "y", "z", "w")));
        signatures.put("D", new LinkedHashSet<>(List.of("x", "y", "z")));
        signatures.put("E", new LinkedHashSet<>(List.of("x", "q")));
        // D shares 3 of C's 4 (75%); E only 1 of their 5.
        assertThat(FieldDocs.similarGroups(signatures)).containsExactly(List.of("A"), List.of("B"),
                List.of("C", "D"), List.of("E"));
    }

    @Test
    void pathsAreComparedWithoutTheSideAnObjectIsOn() {
        // The schema says which steps are sides: siblings of one type that hold elements.
        assertThat(compared("EventDetail/Move/Source/Folder/Id")).isEqualTo("EventDetail/*/Folder/Id")
                .isEqualTo(compared("EventDetail/Create/Folder/Id"));
        assertThat(compared("EventDetail/Update/After/Object/Name")).isEqualTo("EventDetail/*/Object/Name");
        assertThat(compared("EventDetail/Authorise/AddGroups/Group/Name"))
                .isEqualTo(compared("EventDetail/Authorise/RemoveGroups/Group/Name"))
                .isEqualTo("EventDetail/*/Group/Name");
        assertThat(compared("EventDetail/Process/Input/File/Path"))
                .isEqualTo(compared("EventDetail/Process/Output/File/Path"))
                .isEqualTo("EventDetail/*/File/Path");
        // Below the action element too: an Update's Changes, and a Network's action and its two ends.
        assertThat(compared("EventDetail/Update/Changes/Add/Object/Id"))
                .isEqualTo(compared("EventDetail/Update/Changes/Remove/Object/Id"))
                .isEqualTo("EventDetail/*/Changes/Object/Id");
        assertThat(compared("EventDetail/Network/Connect/Source/Device/IPAddress"))
                .isEqualTo(compared("EventDetail/Network/Close/Destination/Device/IPAddress"))
                .isEqualTo("EventDetail/*/Device/IPAddress");
        assertThat(compared("EventDetail/Send/Source/User/Id")).isEqualTo("EventDetail/*/User/Id");
        // An object's own fields are kept: an Email's From and To are two fields of one event.
        assertThat(compared("EventDetail/Create/Email/To/Id")).isEqualTo("EventDetail/*/Email/To/Id");
        assertThat(compared("EventDetail/Create/Email/From/Id")).isEqualTo("EventDetail/*/Email/From/Id");
        // Without the schema's word, the action element alone is any.
        assertThat(compared("EventDetail/Authorise/Data[@Name='x']/@Value"))
                .isEqualTo("EventDetail/*/Data[@Name='x']/@Value");
        assertThat(compared("EventDetail/TypeId")).isEqualTo("EventDetail/TypeId");
        assertThat(FieldDocs.comparedPath("EventDetail/Move/Source/Folder/Id", null))
                .isEqualTo("EventDetail/*/Source/Folder/Id");
        assertThat(compared(null)).isEmpty();
    }

    @Test
    void theActionElementOfAPathIsAny() {
        assertThat(FieldDocs.anyActionPath("EventDetail/Send/Source/User/Id"))
                .isEqualTo("EventDetail/*/Source/User/Id");
        assertThat(FieldDocs.anyActionPath("EventDetail/TypeId")).isEqualTo("EventDetail/TypeId");
        assertThat(FieldDocs.anyActionPath("EventDetail/Data/x")).isEqualTo("EventDetail/Data/x");
        assertThat(FieldDocs.anyActionPath(null)).isEmpty();
        assertThat(FieldDocs.actionOf("EventDetail/Authenticate[1]/User/Id")).isEqualTo("Authenticate");
        assertThat(FieldDocs.actionOf("EventDetail/TypeId")).isNull();
        assertThat(FieldDocs.actionOf("EventDetail/*/User/Id")).isNull();
        assertThat(FieldDocs.actionOf(null)).isNull();
    }

    @Test
    void eachSchemaDescriptionIsItsGolden() {
        final Map<String, Object> golden = FieldDocFixtures.goldenJson("fielddoc/schema-descriptions.json");
        golden.forEach((path, said) -> assertThat(FieldDocs.schemaDescription(FieldDocFixtures.SCHEMA, path))
                .as(path)
                .isEqualTo(said));
    }

    @Test
    void theSchemasOpeningGoesAndADataElementIsNamedByItsName() {
        // Seen in testing: a Data element took the description of the element it is under, repeated for every one.
        assertThat(FieldDocs.schemaDescription(FieldDocFixtures.SCHEMA,
                "EventDetail/Send/Data[@Name='subject']/@Value"))
                .isEqualTo("The `subject` value recorded in a Data element of Send.");
        assertThat(FieldDocs.schemaDescription(FieldDocFixtures.SCHEMA, "EventTime/TimeCreated"))
                .isEqualTo("Time the event was created.");
        assertThat(FieldDocs.schemaDescription(FieldDocFixtures.SCHEMA, "EventSource"))
                .startsWith("Where the event came from");
        assertThat(FieldDocs.schemaDescription(null, "EventSource/User/Id")).isEmpty();
        assertThat(FieldDocs.schemaDescription(null, "@StreamId"))
                .isEqualTo("The id of the Events stream the event came from.");
    }

    @Test
    void aFieldIsDescribedOnlyByWhatTheOtherColumnsDoNotShow() {
        // Only what the other columns do not show: several values in one document; the kind, when nothing else
        // says.
        assertThat(FieldDocs.fieldDescription("The user.", List.of(List.of("a"), List.of("b", "c"))))
                .isEqualTo("The user. Several values per document.");
        assertThat(FieldDocs.fieldDescription("", List.of(List.of("200"), List.of(), List.of("404"))))
                .isEqualTo("Numbers.");
        assertThat(FieldDocs.fieldDescription("", List.of(List.of("x"), List.of("y"))))
                .isEqualTo("Not described: no plan or schema covers it.");
        assertThat(FieldDocs.fieldDescription("", null)).isEmpty();
        assertThat(FieldDocs.fieldDescription(null, List.of(List.of("10.0.0.1"), List.of("::1"))))
                .isEqualTo("IP addresses.");
        assertThat(FieldDocs.fieldDescription("", List.of(List.of("a@x.org"))))
                .isEqualTo("Email addresses.");
        assertThat(FieldDocs.fieldDescription("", List.of(List.of("2026-10-01T10:00:00Z"))))
                .isEqualTo("Timestamps.");
    }

    @Test
    void descriptionsAreKeptWithTheDocumentationDashesAndAll() {
        final String block = FieldDocs.descriptionsBlock(Map.of("a", "x -- y"));
        final String body = block.split("\n", 2)[1];
        assertThat(body.substring(0, body.lastIndexOf('\n'))).doesNotContain("--");
        assertThat(FieldDocs.readDescriptions("# Doc\n\n" + block + "\n")).isEqualTo(Map.of("a", "x -- y"));
        assertThat(FieldDocs.descriptionsBlock(Map.of())).isEmpty();
        assertThat(FieldDocs.readDescriptions("<!-- stroom-mcp field descriptions\n{broken\n-->")).isEmpty();
        assertThat(FieldDocs.readDescriptions("<!-- stroom-mcp field descriptions\n[1]\n-->")).isEmpty();
        assertThat(FieldDocs.readDescriptions("<!-- stroom-mcp field descriptions\n{\"a\": \"b\", \"c\": 1}\n-->"))
                .isEqualTo(Map.of("a", "b"));
        assertThat(FieldDocs.readDescriptions(null)).isEmpty();
    }

    @Test
    void aDescriptionsBlockIsTheServersJson() {
        final Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("user.name", "Who \"logged\" in—or not");
        descriptions.put("b", "line\none");
        assertThat(FieldDocs.descriptionsBlock(descriptions)).isEqualTo("<!-- stroom-mcp field descriptions\n{\n"
                                                                        + " \"user.name\": \"Who \\\"logged\\\" in"
                                                                        + "—or not\",\n \"b\": \"line\\none\"\n"
                                                                        + "}\n-->");
    }

    @Test
    void aSourceAndAConditionAreSaidForAReader() {
        final FieldMapping entry = FieldMapping.fromJson(FieldDocFixtures.json("""
                {"path": "EventSource/User/Id", "field": "user", "transform": "lower", "map": {"a": "b"},
                 "default": "c"}"""));
        assertThat(FieldDocs.sourceText(entry)).isEqualTo("`user` (lower): a -> b; otherwise c");
        final FieldMapping repeated = FieldMapping.fromJson(FieldDocFixtures.json("""
                {"path": "EventDetail/View/Data", "data_name": "r", "field": "user", "transform": "lower",
                 "repeat": true}"""));
        assertThat(FieldDocs.sourceText(repeated)).isEqualTo("`user` (lower), one per value");
        final Condition condition = Condition.fromJson(FieldDocFixtures.json("""
                {"field": "action", "one_of": ["a", "b"], "scope": "record"}"""));
        assertThat(FieldDocs.conditionText(condition)).isEqualTo("`action` (of the record) in a, b");
        assertThat(FieldDocs.conditionsText(List.of())).isEmpty();
    }

    @Test
    void descriptionsGivenWinAndTheRestAreAskedForOnce() {
        final Descriptions given = new Descriptions(Map.of("a", "  The a.  ", "b", " "), true);
        assertThat(given.given()).isEqualTo(Map.of("a", "The a."));
        assertThat(given.of("a", "src", List.of("1"), "schema words")).isEqualTo("The a.");
        assertThat(given.of("b", "src", List.of("1"), "schema words")).isEqualTo("schema words");
        assertThat(given.of("b", "other", List.of("2"), "")).isEmpty();
        // The server's own ids are never asked.
        assertThat(given.of("StreamId", "@StreamId", List.of("7"), "")).isEmpty();
        assertThat(given.wanted().keySet()).containsExactly("b");
        assertThat(given.wanted().get("b").get("from")).isEqualTo("src");
        assertThat(new Descriptions().of("x", "s", List.of(), "words")).isEqualTo("words");
        assertThat(new Descriptions().wanted()).isEmpty();
    }

    @Test
    void numbersAreFormattedWithTheirRounding() {
        assertThat(ToolText.formatG(30.0)).isEqualTo("30");
        assertThat(ToolText.formatG(66.66666)).isEqualTo("66.6667");
        assertThat(ToolText.formatG(100.0)).isEqualTo("100");
        assertThat(ToolText.formatG(0.00001)).isEqualTo("1e-05");
        assertThat(ToolText.formatG(1234567.0)).isEqualTo("1.23457e+06");
        assertThat(ToolText.formatFixed0(12.5)).isEqualTo("12");
        assertThat(ToolText.formatFixed0(66.666)).isEqualTo("67");
        assertThat(ToolText.strip("  x \n")).isEqualTo("x");
        assertThat(ToolText.head("abc", 2)).isEqualTo("ab");
        assertThat(ToolText.split("a//b/", "/")).containsExactly("a", "", "b", "");
    }

    private static String compared(final String path) {
        return FieldDocs.comparedPath(path, FieldDocFixtures.SCHEMA);
    }
}
