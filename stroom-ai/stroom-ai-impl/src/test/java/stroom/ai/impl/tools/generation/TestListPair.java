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

/// An entry's `pair` ([ListPair]): one pair of a name/value list read by its name, and a Data per pair but those read
/// elsewhere.
class TestListPair extends XsltRuns {

    private static final String RECORDS = """
            <array xmlns="http://www.w3.org/2013/XSL/json"><map>
            <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>
            <array key="Parameters">\
            <map><string key="Name">Name</string><string key="Value">FW to personal</string></map>\
            <map><string key="Name">From</string><string key="Value">boss@example.org</string></map>\
            <map><string key="Name">MoveToFolder</string><string key="Value">Old</string></map>\
            <map><string key="Name">Empty</string><string key="Value"></string></map>\
            </array>
            </map></array>""";

    private static Map<String, Object> entry(final String text) {
        return json(text);
    }

    private static TranslationMapping ruleMapping(final Map<?, ?>... fields) {
        final List<Object> all = new ArrayList<>(List.of(entry("""
                {"path": "EventDetail/TypeId", "value": "Create-InboxRule"}""")));
        all.addAll(List.of(fields));
        return mapping(with("input", "json", "common", BASE.subList(0, 5), "events", List.of(rule("create",
                List.of(), all))));
    }

    @Test
    void aPairIsReadByItsNameAndTheOthersAreDataNamedByTheirNames() {
        final TranslationMapping mapping = ruleMapping(entry("""
                {"path": "EventDetail/Create/Configuration/Name", "pair": {"list": "Parameters", "name": "Name",
                 "name_key": "Name", "value_key": "Value"}}"""), entry("""
                {"path": "EventDetail/Create/Data", "pair": {"list": "Parameters", "name_key": "Name",
                 "value_key": "Value"}}"""));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement event = children(transform(result.xslt(), RECORDS), "Event").getFirst();

        assertThat(text(event, "EventDetail/Create/Configuration/Name")).isEqualTo("FW to personal");
        final XmlElement create = find(event, "EventDetail/Create");
        assertThat(having(create, EVT, "Data", "Name", "From")).singleElement()
                .satisfies(data -> assertThat(data.get("Value")).isEqualTo("boss@example.org"));
        assertThat(having(create, EVT, "Data", "Name", "MoveToFolder")).hasSize(1);
        // The pair read by its name is written once, and a pair with no value not at all.
        assertThat(having(create, EVT, "Data", "Name", "Name")).isEmpty();
        assertThat(having(create, EVT, "Data", "Name", "Empty")).isEmpty();
    }

    @Test
    void aPairKeptAsNamedDataIsWrittenThereOnly() {
        final TranslationMapping mapping = ruleMapping(entry("""
                {"path": "EventDetail/Create/Configuration/Name", "value": "rule"}"""), entry("""
                {"path": "EventDetail/Create/Data", "data_name": "folder", "pair": {"list": "Parameters",
                 "name": "MoveToFolder", "name_key": "Name", "value_key": "Value"}}"""), entry("""
                {"path": "EventDetail/Create/Data", "pair": {"list": "Parameters", "name_key": "Name",
                 "value_key": "Value"}}"""));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement create = find(children(transform(result.xslt(), RECORDS), "Event").getFirst(),
                "EventDetail/Create");

        assertThat(having(create, EVT, "Data", "Name", "folder")).hasSize(1);
        assertThat(having(create, EVT, "Data", "Name", "MoveToFolder")).isEmpty();
        assertThat(having(create, EVT, "Data", "Name", "Name")).hasSize(1);
    }

    @Test
    void itemsHoldingTheirValuesUnderKeysOfTheirOwnAreReadByTheirOtherKey() {
        final TranslationMapping mapping = ruleMapping(entry("""
                {"path": "EventDetail/Create/Configuration/Name", "pair": {"list": "parameters", "name": "USER_EMAIL",
                 "name_key": "name", "value_key": "*"}}"""), entry("""
                {"path": "EventDetail/Create/Data", "pair": {"list": "parameters", "name_key": "name",
                 "value_key": "*"}}"""));
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement event = children(transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>
                <array key="parameters">\
                <map><string key="name">USER_EMAIL</string><string key="value">a@x.org</string></map>\
                <map><string key="name">IS_ADMIN</string><boolean key="boolValue">true</boolean></map>\
                <map><string key="name">GROUPS</string><array key="multiValue"><string>a</string>\
                <string>b</string></array></map>\
                </array>
                </map></array>"""), "Event").getFirst();

        assertThat(text(event, "EventDetail/Create/Configuration/Name")).isEqualTo("a@x.org");
        final XmlElement create = find(event, "EventDetail/Create");
        assertThat(having(create, EVT, "Data", "Name", "IS_ADMIN")).singleElement()
                .satisfies(data -> assertThat(data.get("Value")).isEqualTo("true"));
        assertThat(having(create, EVT, "Data", "Name", "GROUPS")).singleElement()
                .satisfies(data -> assertThat(data.get("Value")).isEqualTo("a, b"));
        assertThat(having(create, EVT, "Data", "Name", "USER_EMAIL")).isEmpty();
    }

    @Test
    void theMappingKeepsThePairFormNotItsXpath() {
        final FieldMapping read = FieldMapping.fromJson(json("""
                {"path": "EventDetail/Create/Configuration/Name", "pair": {"list": "event.Parameters",
                 "name": "Name", "name_key": "Name", "value_key": "Value"}}"""));

        assertThat(read.xpath()).isEqualTo(
                "*[@key='event']/*[@key='Parameters']/*[*[@key='Name'] = 'Name']/*[@key='Value']");
        assertThat(read.toJson(DumpMode.WITHOUT_NONE)).doesNotContainKey("xpath").containsKey("pair");
        assertThat(FieldMapping.fromJson(read.toJson(DumpMode.WITHOUT_NONE))).isEqualTo(read);
    }

    @Test
    void aPairNeedsItsKeysAndADataPerPairNothingElse() {
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Create/Configuration/Name", "pair": {"list": "Parameters", "name": "Name"}}""")))
                .hasMessageContaining("name_key");
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Create/Configuration/Name", "pair": {"list": "Parameters",
                 "name_key": "Name", "value_key": "Value"}}""")))
                .hasMessageContaining("path ends in Data");
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Create/Data", "data_name": "x", "pair": {"list": "Parameters",
                 "name_key": "Name", "value_key": "Value"}}""")))
                .hasMessageContaining("path and pair only");
        assertThatThrownBy(() -> FieldMapping.fromJson(json("""
                {"path": "EventDetail/Create/Configuration/Name", "field": "x", "pair": {"list": "Parameters",
                 "name": "Name", "name_key": "Name", "value_key": "Value"}}""")))
                .hasMessageContaining("exactly one of");
    }

    @Test
    void aPairReadsAJsonInputsList() {
        assertThatThrownBy(() -> mapping(with("input", "data_splitter", "common", BASE,
                "events", List.of(rule("create", List.of(), List.of(json("""
                        {"path": "EventDetail/TypeId", "value": "x"}"""), entry("""
                        {"path": "EventDetail/Create/Data", "pair": {"list": "Parameters", "name_key": "Name",
                         "value_key": "Value"}}""")))))))
                .hasMessageContaining("JSON input");
    }
}
