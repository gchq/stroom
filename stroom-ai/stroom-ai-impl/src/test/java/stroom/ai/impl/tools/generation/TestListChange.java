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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An entry's `change` ([ListChange]): an old/new property-change list written as an Update's Before and After
/// snapshots, a property placed by name typed in both, the others Data named by their properties.
class TestListChange extends XsltRuns {

    // One record's changes: JSON text of a string and of one-value arrays, a property added (no old value), one
    // removed (no new value), a list-valued one held as JSON text, and one held as a JSON array.
    private static final String RECORDS = """
            <array xmlns="http://www.w3.org/2013/XSL/json"><map>
            <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>
            <array key="ModifiedProperties">\
            <map><string key="Name">DisplayName</string><string key="OldValue">"Sam T"</string>\
            <string key="NewValue">"Sam Taylor"</string></map>\
            <map><string key="Name">AccountEnabled</string><string key="OldValue">["true"]</string>\
            <string key="NewValue">["false"]</string></map>\
            <map><string key="Name">JobTitle</string><string key="OldValue">[]</string>\
            <string key="NewValue">["Analyst"]</string></map>\
            <map><string key="Name">Manager</string><string key="OldValue">kate</string>\
            <null key="NewValue"/></map>\
            <map><string key="Name">Licences</string><string key="OldValue">["E3"]</string>\
            <string key="NewValue">["E3","E5"]</string></map>\
            <map><string key="Name">Tags</string><array key="OldValue"><string>a</string></array>\
            <array key="NewValue"><string>a</string><string>b</string></array></map>\
            </array>
            </map></array>""";
    private static final String CHANGE = """
            "list": "ModifiedProperties", "name_key": "Name", "old_key": "OldValue", "new_key": "NewValue\"""";

    private static Map<String, Object> entry(final String text) {
        return json(text);
    }

    private static TranslationMapping ruleMapping(final Map<?, ?>... fields) {
        final List<Object> all = new ArrayList<>(List.of(entry("""
                {"path": "EventDetail/TypeId", "value": "Update-User"}""")));
        all.addAll(List.of(fields));
        return mapping(with("input", "json", "common", BASE.subList(0, 5), "events", List.of(rule("update",
                List.of(), all))));
    }

    private static Map<String, Object> snapshots(final String side) {
        return entry("{\"path\": \"EventDetail/Update/" + side + "/User/Data\", \"change\": {" + CHANGE + "}}");
    }

    private static Map<String, Object> named(final String side) {
        return entry("{\"path\": \"EventDetail/Update/" + side + "/User/Name\", \"change\": {" + CHANGE
                     + ", \"name\": \"DisplayName\"}}");
    }

    @Test
    void aPropertyPlacedByNameIsTypedInBothSnapshotsAndTheOthersAreDataOfTheirSide() {
        final TranslationMapping mapping = ruleMapping(named("Before"), named("After"), snapshots("Before"),
                snapshots("After"));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement event = children(events, "Event").getFirst();

        // JSON text of one value unwrapped, the property typed in both and not Data too.
        assertThat(text(event, "EventDetail/Update/Before/User/Name")).isEqualTo("Sam T");
        assertThat(text(event, "EventDetail/Update/After/User/Name")).isEqualTo("Sam Taylor");
        final XmlElement before = find(event, "EventDetail/Update/Before/User");
        final XmlElement after = find(event, "EventDetail/Update/After/User");
        assertThat(having(before, EVT, "Data", "Name", "DisplayName")).isEmpty();
        assertThat(having(after, EVT, "Data", "Name", "DisplayName")).isEmpty();
        assertThat(value(before, "AccountEnabled")).isEqualTo("true");
        assertThat(value(after, "AccountEnabled")).isEqualTo("false");
        // Added: in After only; removed: in Before only.
        assertThat(having(before, EVT, "Data", "Name", "JobTitle")).isEmpty();
        assertThat(value(after, "JobTitle")).isEqualTo("Analyst");
        assertThat(value(before, "Manager")).isEqualTo("kate");
        assertThat(having(after, EVT, "Data", "Name", "Manager")).isEmpty();
        // A longer array as JSON text stays as its text; an array as parsed is joined, as a pair's is.
        assertThat(value(before, "Licences")).isEqualTo("E3");
        assertThat(value(after, "Licences")).isEqualTo("[\"E3\",\"E5\"]");
        assertThat(value(before, "Tags")).isEqualTo("a");
        assertThat(value(after, "Tags")).isEqualTo("a, b");
    }

    @Test
    void afterHoldsTheObjectWhateverTheListHolds() {
        final TranslationMapping mapping = ruleMapping(snapshots("Before"), snapshots("After"));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>
                <array key="ModifiedProperties"/>
                </map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement event = children(events, "Event").getFirst();
        assertThat(find(event, "EventDetail/Update/After/User")).isNotNull();
        assertThat(descendant(find(event, "EventDetail/Update"), "Before")).isNull();
    }

    @Test
    void eachTargetIsAnObjectOfItsOwnInBothSnapshotsInTheSameOrder() {
        final String group = """
                {"path": "EventDetail/Update/%s/User", "for_each": {"array": "targetResources"}, "as": "target",
                 "fields": [{"path": "Id", "field": "id"},
                            {"path": "Name", "change": {"list": "modifiedProperties", "name": "DisplayName",
                             "name_key": "displayName", "old_key": "oldValue", "new_key": "newValue"}},
                            {"path": "Data", "change": {"list": "modifiedProperties", "name_key": "displayName",
                             "old_key": "oldValue", "new_key": "newValue"}}]}""";
        final TranslationMapping mapping = ruleMapping(entry(group.formatted("Before")),
                entry(group.formatted("After")));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>
                <array key="targetResources">\
                <map><string key="id">u-1</string><array key="modifiedProperties">\
                <map><string key="displayName">DisplayName</string><string key="oldValue">["Sam T"]</string>\
                <string key="newValue">["Sam Taylor"]</string></map>\
                <map><string key="displayName">City</string><string key="oldValue">[]</string>\
                <string key="newValue">["Perth"]</string></map></array></map>\
                <map><string key="id">u-2</string><array key="modifiedProperties">\
                <map><string key="displayName">AccountEnabled</string><string key="oldValue">[true]</string>\
                <string key="newValue">[false]</string></map></array></map>\
                </array>
                </map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement update = find(children(events, "Event").getFirst(), "EventDetail/Update");
        final List<XmlElement> before = children(find(update, "Before"), "User");
        final List<XmlElement> after = children(find(update, "After"), "User");
        assertThat(before).hasSize(2);
        assertThat(after).hasSize(2);
        assertThat(text(before.get(0), "Id")).isEqualTo("u-1");
        assertThat(text(before.get(0), "Name")).isEqualTo("Sam T");
        assertThat(text(after.get(0), "Name")).isEqualTo("Sam Taylor");
        assertThat(having(before.get(0), EVT, "Data", "Name", "City")).isEmpty();
        assertThat(value(after.get(0), "City")).isEqualTo("Perth");
        assertThat(text(after.get(1), "Id")).isEqualTo("u-2");
        assertThat(value(before.get(1), "AccountEnabled")).isEqualTo("true");
        assertThat(value(after.get(1), "AccountEnabled")).isEqualTo("false");
    }

    @Test
    void theMappingKeepsTheChangeFormItsSideThePaths() {
        final FieldMapping read = FieldMapping.fromJson(named("Before"));

        assertThat(read.change().before()).isTrue();
        assertThat(FieldMapping.fromJson(named("After")).change().before()).isFalse();
        assertThat(read.xpath()).contains("*[@key='OldValue']").doesNotContain("NewValue");
        assertThat(read.toJson(DumpMode.WITHOUT_NONE)).doesNotContainKey("xpath").containsKey("change");
        assertThat(FieldMapping.fromJson(read.toJson(DumpMode.WITHOUT_NONE))).isEqualTo(read);
    }

    @Test
    void aChangeIsBelowBeforeOrAfterWithItsThreeKeys() {
        assertThatThrownBy(() -> FieldMapping.fromJson(entry("""
                {"path": "EventDetail/Update/Data", "change": {%s}}""".formatted(CHANGE))))
                .hasMessageContaining("below Before");
        assertThatThrownBy(() -> FieldMapping.fromJson(entry("""
                {"path": "EventDetail/Update/After/User/Name", "change": {"list": "ModifiedProperties",
                 "name": "DisplayName", "name_key": "Name", "old_key": "Value", "new_key": "Value"}}""")))
                .hasMessageContaining("each a different key");
        assertThatThrownBy(() -> FieldMapping.fromJson(entry("""
                {"path": "EventDetail/Update/After/User/Name", "change": {%s}}""".formatted(CHANGE))))
                .hasMessageContaining("path ends in Data");
        assertThatThrownBy(() -> FieldMapping.fromJson(entry("""
                {"path": "EventDetail/Update/After/User/Data", "data_name": "x", "change": {%s}}"""
                .formatted(CHANGE))))
                .hasMessageContaining("path and change only");
        assertThatThrownBy(() -> mapping(with("input", "data_splitter", "common", BASE,
                "events", List.of(rule("update", List.of(), List.of(json("""
                        {"path": "EventDetail/TypeId", "value": "x"}"""), snapshots("After")))))))
                .hasMessageContaining("JSON input");
    }

    // The Value of the one Data of the name in an element.
    private static String value(final XmlElement in, final String name) {
        final List<XmlElement> found = having(in, EVT, "Data", "Name", name);
        assertThat(found).as(name).hasSize(1);
        return found.getFirst().get("Value");
    }
}
