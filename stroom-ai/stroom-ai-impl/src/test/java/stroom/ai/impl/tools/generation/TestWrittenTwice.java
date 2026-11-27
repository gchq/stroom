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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The warning of an input a rule writes twice, as Data and Data or as Data and an element. Seen in chat 182 (rule
/// update_inboxrule): `event.Parameters`' items written as Data of `EventDetail/Update/After/Object` (the agent's
/// change) and as Data of `EventDetail/Update` (the draft's, left in as the agent's removal named another path).
class TestWrittenTwice {

    private static final String DOCUMENT = "{\"path\": \"EventDetail/View/Document/Id\", \"field\": \"DocId\"}";
    private static final String PARAMETERS_GROUP = """
            {"path": "%s", "data_name": "event.Parameters", "for_each": {"array": "event.Parameters"},
             "fields": [{"path": "Data", "data_name": "Name", "field": "Name"},
                        {"path": "Data", "data_name": "Value", "field": "Value"}]}""";

    private static List<String> warnings(final List<Map<String, Object>> rules) {
        return warnings(List.of(), rules);
    }

    private static List<String> warnings(final List<String> moreCommon, final List<Map<String, Object>> rules) {
        final Map<String, Object> json = new LinkedHashMap<>(GenerationFixtures.json("""
                {"input": "json",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                            {"path": "EventSource/System/Name", "value": "Exchange"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Generator", "value": "Exchange"},
                            {"path": "EventSource/Device/HostName", "value": "exchange"},
                            {"path": "EventSource/User/Id", "field": "UserId"}]}"""));
        final List<Object> common = new ArrayList<>((List<?>) json.get("common"));
        moreCommon.forEach(entry -> common.add(GenerationFixtures.json(entry)));
        json.put("common", common);
        json.put("events", rules);
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(json),
                GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        return generated.warnings().stream().filter(warning -> warning.contains("written twice")).toList();
    }

    private static Map<String, Object> rule(final String name, final String... fields) {
        final List<Object> entries = new ArrayList<>();
        entries.add(GenerationFixtures.json("{\"path\": \"EventDetail/TypeId\", \"value\": \"" + name + "\"}"));
        for (final String field : fields) {
            entries.add(GenerationFixtures.json(field));
        }
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        rule.put("when", List.of(Map.of("field", "Operation", "equals", name)));
        rule.put("fields", entries);
        return rule;
    }

    @Test
    void theSameItemsWrittenAsDataInTwoPlacesAreWarnedOfOnceWithTheRemoval() {
        final List<String> warnings = warnings(List.of(rule("update_inboxrule",
                "{\"path\": \"EventDetail/Update/After/Object/Type\", \"value\": \"InboxRule\"}",
                PARAMETERS_GROUP.formatted("EventDetail/Update/After/Object/Data"),
                PARAMETERS_GROUP.formatted("EventDetail/Update/Data"))));

        assertThat(warnings).singleElement().asString()
                .startsWith("[update_inboxrule] the same input is written twice: 'Name' (of each item of "
                            + "`event.Parameters`) as EventDetail/Update/After/Object/Data[@Name='event.Parameters']"
                            + "/Data[@Name='Name'] and as EventDetail/Update/Data[@Name='event.Parameters']"
                            + "/Data[@Name='Name']; 'Value' (of each item of `event.Parameters`) as ")
                .contains("so each event holds it twice. Remove one")
                .endsWith("e.g. changes={\"events\": [{\"name\": \"update_inboxrule\", \"fields\": [{\"path\": "
                          + "\"EventDetail/Update/Data\", \"data_name\": \"event.Parameters\", \"remove\": true}]}]}");
    }

    @Test
    void aFieldWrittenAsDataAndAsAnElementInAGroupIsWarnedOf() {
        final List<String> warnings = warnings(List.of(rule("send",
                "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                "{\"path\": \"EventDetail/Send/Destination/User\", \"for_each\": {\"array\": \"To\"},"
                + " \"fields\": [{\"path\": \"Id\", \"field\": \"Address\"}]}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"To\", \"for_each\": {\"array\": \"To\"},"
                + " \"fields\": [{\"path\": \"Data\", \"data_name\": \"Address\", \"field\": \"Address\"}]}")));

        assertThat(warnings).singleElement().asString()
                .contains("'Address' (of each item of `To`) as EventDetail/Send/Destination/User/Id and as "
                          + "EventDetail/Send/Data[@Name='To']/Data[@Name='Address']")
                .contains("\"path\": \"EventDetail/Send/Data\", \"data_name\": \"To\", \"remove\": true");
    }

    @Test
    void twoCopiesInOneGroupAreWarnedOfWithTheGroupToSendAgain() {
        final List<String> warnings = warnings(List.of(rule("send",
                "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                "{\"path\": \"EventDetail/Send/Destination/User\", \"for_each\": {\"array\": \"To\"},"
                + " \"fields\": [{\"path\": \"Id\", \"field\": \"Address\"},"
                + " {\"path\": \"Data\", \"data_name\": \"address\", \"field\": \"Address\"}]}")));

        assertThat(warnings).singleElement().asString()
                .contains("'Address' (of each item of `To`) as EventDetail/Send/Destination/User/Id and as "
                          + "EventDetail/Send/Destination/User/Data[@Name='address']")
                .endsWith("both are in the group EventDetail/Send/Destination/User, so send it again (changes, by "
                          + "its path) without EventDetail/Send/Destination/User/Data[@Name='address']");
    }

    @Test
    void aTopLevelDataWrittenTwiceIsWarnedOf() {
        final List<String> warnings = warnings(List.of(rule("send",
                "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                "{\"path\": \"EventDetail/Send/Destination/User/Id\", \"field\": \"Recipient\"}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"subject\", \"field\": \"Subject\"}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"Subject\", \"field\": \"Subject\"}")));

        assertThat(warnings).singleElement().asString()
                .contains("'Subject' as EventDetail/Send/Data[@Name='subject'] and as "
                          + "EventDetail/Send/Data[@Name='Subject']")
                .contains("\"data_name\": \"Subject\", \"remove\": true");
    }

    @Test
    void aCommonEntryAndARulesEntryWritingTheSameInputAsDataAreWarnedOf() {
        // A common entry is written in every rule's events: a rule's Data of the same input says it again.
        final List<String> warnings = warnings(List.of(
                "{\"path\": \"EventSource/Device/Data\", \"data_name\": \"ClientIP\", \"field\": \"ClientIP\"}"),
                List.of(rule("view", DOCUMENT,
                        "{\"path\": \"EventDetail/View/Data\", \"data_name\": \"client\", \"field\": "
                        + "\"ClientIP\"}")));

        assertThat(warnings).singleElement().asString()
                .startsWith("[view] the same input is written twice: 'ClientIP' as "
                            + "EventSource/Device/Data[@Name='ClientIP'] and as EventDetail/View/Data[@Name='client']")
                // The rule's, not the common one every rule's events hold, is the one to remove.
                .endsWith("e.g. changes={\"events\": [{\"name\": \"view\", \"fields\": [{\"path\": "
                          + "\"EventDetail/View/Data\", \"data_name\": \"client\", \"remove\": true}]}]}");
    }

    @Test
    void aCommonDataBesideARulesTypedElementIsWarnedOfAsTheCommonOneToRemove() {
        // In a group (so not the top-level element-and-Data case EventTrees words itself), the common Data is the one
        // to remove, from common, unless another rule needs it.
        final List<String> warnings = warnings(List.of(
                "{\"path\": \"EventSource/Device/Data\", \"data_name\": \"To\", \"for_each\": {\"array\": \"To\"}, "
                + "\"fields\": [{\"path\": \"Data\", \"data_name\": \"Address\", \"field\": \"Address\"}]}"),
                List.of(rule("send",
                        "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                        "{\"path\": \"EventDetail/Send/Destination/User\", \"for_each\": {\"array\": \"To\"},"
                        + " \"fields\": [{\"path\": \"Id\", \"field\": \"Address\"}]}")));

        assertThat(warnings).singleElement().asString()
                .contains("'Address' (of each item of `To`) as EventSource/Device/Data[@Name='To']"
                          + "/Data[@Name='Address'] and as EventDetail/Send/Destination/User/Id")
                .contains("a common entry, written in every rule's events: remove it only if no other rule needs it")
                .endsWith("e.g. changes={\"common\": [{\"path\": \"EventSource/Device/Data\", \"data_name\": \"To\", "
                          + "\"remove\": true}]}");
    }

    @Test
    void aRepeatedDataAndAGroupOverTheSameFieldAreTheSameInput() {
        // repeat writes each value of To, as the group over To does with its item.
        final List<String> warnings = warnings(List.of(rule("send",
                "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                "{\"path\": \"EventDetail/Send/Destination/User\", \"for_each\": {\"array\": \"To\"},"
                + " \"fields\": [{\"path\": \"Id\", \"item\": true}]}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"To\", \"field\": \"To\", "
                + "\"repeat\": true}")));

        assertThat(warnings).singleElement().asString()
                .contains("as EventDetail/Send/Destination/User/Id and as EventDetail/Send/Data[@Name='To']")
                .contains("\"path\": \"EventDetail/Send/Data\", \"data_name\": \"To\", \"remove\": true");

        // A group over another field, or read another way, is another input.
        assertThat(warnings(List.of(rule("send",
                "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\"}",
                "{\"path\": \"EventDetail/Send/Destination/User\", \"for_each\": {\"array\": \"Cc\"},"
                + " \"fields\": [{\"path\": \"Id\", \"item\": true}]}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"To\", \"field\": \"To\", "
                + "\"repeat\": true}",
                "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"Cc\", \"field\": \"Cc\", "
                + "\"repeat\": true, \"transform\": \"lower\"}")))).isEmpty();
    }

    @Test
    void twoTypedHomesOrTwoRulesOrTwoConversionsAreNotWarnedOf() {
        final List<String> warnings = warnings(List.of(
                // The account that acted, written to two typed elements: the schema's own shape.
                rule("send",
                        "{\"path\": \"EventDetail/Send/Source/User/Id\", \"field\": \"UserId\", "
                        + "\"transform\": \"strip_domain\"}",
                        "{\"path\": \"EventDetail/Send/Destination/User/Id\", \"field\": \"UserId\", "
                        + "\"transform\": \"strip_domain\"}",
                        // Written differently: not the same value.
                        "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"user\", \"field\": \"UserId\"}",
                        "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"user_upper\", \"field\": \"UserId\", "
                        + "\"transform\": \"upper\"}",
                        // A constant is no input.
                        "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"a\", \"value\": \"x\"}",
                        "{\"path\": \"EventDetail/Send/Data\", \"data_name\": \"b\", \"value\": \"x\"}"),
                // The same Data in two rules: each event holds it once.
                rule("view", DOCUMENT, "{\"path\": \"EventDetail/View/Data\", \"data_name\": \"subject\", "
                                       + "\"field\": \"Subject\"}"),
                rule("view_again", DOCUMENT, "{\"path\": \"EventDetail/View/Data\", \"data_name\": "
                                             + "\"subject\", \"field\": \"Subject\"}")));

        assertThat(warnings).isEmpty();
    }

    @Test
    void aTopLevelElementAndDataAreLeftToTheirOwnWarning() {
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                GenerationFixtures.mapping().toJson(DumpMode.COMPACT)), GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.warnings()).noneMatch(warning -> warning.contains("written twice"));
        assertThat(warnings(List.of(rule("view",
                "{\"path\": \"EventDetail/View/Document/Id\", \"field\": \"sid\"}",
                "{\"path\": \"EventDetail/View/Data\", \"data_name\": \"session\", \"field\": \"sid\"}"))))
                .isEmpty();
    }
}
