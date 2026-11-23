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
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Old/new lists written as a Data per property of each Update snapshot whatever the mapping says of them, with the
/// shapes the models wrote in eval case 76.
class TestSnapshotChanges {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    private static final String PER_PROPERTY = "{path=Data, change={list=modifiedProperties, name_key=displayName, "
                                               + "old_key=oldValue, new_key=newValue}}";
    // Graph-style targets as the records read them (lists as parsed), each with its old/new list.
    private static final List<Object> TARGETS = List.of(
            ToolJson.loads("""
                    [{"id": "u-1", "type": "User", "userPrincipalName": "sam@x", "modifiedProperties": [
                      {"displayName": "DisplayName", "oldValue": "[\\"Sam T\\"]", "newValue": "[\\"Sam Taylor\\"]"}]},
                     {"id": "g-1", "type": "Group", "modifiedProperties": [{"displayName": "Description",
                      "oldValue": "[]", "newValue": "[\\"Ops team\\"]"}]}]"""),
            ToolJson.loads("""
                    [{"id": "u-2", "type": "User", "userPrincipalName": "kate@x", "modifiedProperties": [
                      {"displayName": "AccountEnabled", "oldValue": "[true]", "newValue": "[false]"},
                      {"displayName": "DisplayName", "oldValue": "[\\"Kate\\"]",
                       "newValue": "[\\"Kate Jones\\"]"}]}]"""),
            ToolJson.loads("""
                    [{"id": "u-3", "type": "User", "userPrincipalName": "al@x", "modifiedProperties": [
                      {"displayName": "AccountEnabled", "oldValue": "[false]", "newValue": "[true]"},
                      {"displayName": "Description", "oldValue": "[]", "newValue": "[\\"Lead\\"]"}]}]"""));
    private static final List<Object> CHANGES = List.of(
            ToolJson.loads("""
                    [{"Name": "DisplayName", "OldValue": "Sam T", "NewValue": "Sam Taylor"},
                     {"Name": "JobTitle", "OldValue": "", "NewValue": "Analyst"}]"""),
            ToolJson.loads("[{\"Name\": \"DisplayName\", \"OldValue\": \"Kate\", \"NewValue\": \"Kate Jones\"}]"),
            ToolJson.loads("""
                    [{"Name": "JobTitle", "OldValue": "Analyst", "NewValue": "Lead"},
                     {"Name": "Department", "OldValue": "Ops", "NewValue": "Finance"}]"""),
            ToolJson.loads("""
                    [{"Name": "DisplayName", "OldValue": "Al", "NewValue": "Al Smith"},
                     {"Name": "Department", "OldValue": "", "NewValue": "Ops"}]"""));

    @Test
    void eachTargetsListWrittenAsAGroupOfNameAndValueDataIsADataPerPropertyOfItsSide() {
        // Seen in eval case 76 (Haiku, build_translation_xslt): each snapshot's targets with a for_each group over
        // their modifiedProperties, a name and a value Data each, the values JSON text.
        final Map<String, Object> mapping = mapping("""
                {"path": "EventDetail/Update/Before/User", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}, {"path": "Name", "field": "userPrincipalName"},
                            {"path": "Data", "data_name": "modifiedProperty",
                             "for_each": {"array": "modifiedProperties"},
                             "fields": [{"path": "Data", "data_name": "name", "field": "displayName"},
                                        {"path": "Data", "data_name": "value", "field": "oldValue"}]}]},
                {"path": "EventDetail/Update/After/User", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}, {"path": "Name", "field": "userPrincipalName"},
                            {"path": "Data", "data_name": "modifiedProperty",
                             "for_each": {"array": "modifiedProperties"},
                             "fields": [{"path": "Data", "data_name": "name", "field": "displayName"},
                                        {"path": "Data", "data_name": "value", "field": "newValue"}]}]}""");

        final List<String> said = SnapshotChanges.apply(mapping, field -> "targetResources".equals(field)
                ? TARGETS
                : List.of(), Map.of());

        final List<Map<String, Object>> fields = fieldsOf(mapping);
        assertThat(fields).extracting(field -> field.get("path")).containsExactly("EventDetail/TypeId",
                "EventDetail/Update/Before/User", "EventDetail/Update/After/User");
        for (final Map<String, Object> group : fields.subList(1, 3)) {
            assertThat(JsonValues.maps(group.get("fields"))).extracting(Object::toString).containsExactly(
                    "{path=Id, field=id}", "{path=Name, field=userPrincipalName}", PER_PROPERTY);
        }
        assertThat(said).containsExactly(
                "'targetResources[].modifiedProperties' read whole at EventDetail/Update/Before/User/Data (its "
                + "JSON run together), written instead as a Data per property of its old values (update)",
                "'targetResources[].modifiedProperties' (an old/new list) written as a Data per property of each "
                + "item's object at EventDetail/Update/Before/User (update)",
                "'targetResources[].modifiedProperties' read whole at EventDetail/Update/After/User/Data (its JSON "
                + "run together), written instead as a Data per property of its new values (update)",
                "'targetResources[].modifiedProperties' (an old/new list) written as a Data per property of each "
                + "item's object at EventDetail/Update/After/User (update)");
        assertThat(XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0").ok()).isTrue();

        // Written so, it is left as it is.
        assertThat(SnapshotChanges.apply(mapping, field -> "targetResources".equals(field)
                ? TARGETS
                : List.of(), Map.of())).isEmpty();
    }

    @Test
    void aRecordsListReadWholeOrByNameIsWrittenPerPropertyOnEachSide() {
        final Map<String, Object> mapping = mapping("""
                {"path": "EventDetail/Update/After/User/Id", "field": "target"},
                {"path": "EventDetail/Update/Before/User/Name", "change": {"list": "ModifiedProperties",
                 "name": "DisplayName", "name_key": "Name", "old_key": "OldValue", "new_key": "NewValue"}},
                {"path": "EventDetail/Update/After/User/Data", "data_name": "ModifiedProperties",
                 "field": "ModifiedProperties"}""");

        final List<String> said = SnapshotChanges.apply(mapping, field -> "ModifiedProperties".equals(field)
                ? CHANGES
                : List.of(), Map.of());

        assertThat(fieldsOf(mapping)).extracting(Object::toString).containsExactly(
                "{path=EventDetail/TypeId, value=Update-User}",
                "{path=EventDetail/Update/After/User/Id, field=target}",
                "{path=EventDetail/Update/Before/User/Name, change={list=ModifiedProperties, name=DisplayName, "
                + "name_key=Name, old_key=OldValue, new_key=NewValue}}",
                "{path=EventDetail/Update/After/User/Data, change={list=ModifiedProperties, name_key=Name, "
                + "old_key=OldValue, new_key=NewValue}}",
                "{path=EventDetail/Update/Before/User/Data, change={list=ModifiedProperties, name_key=Name, "
                + "old_key=OldValue, new_key=NewValue}}");
        assertThat(said).hasSize(2);
        assertThat(XsltGenerator.generate(TranslationMapping.fromJson(mapping), SCHEMA, "4.1.0").ok()).isTrue();
    }

    @Test
    void theUsersAnswerSaysWhichKeyIsOldAndOtherElementsAndInputsAreLeftAsTheyAre() {
        final Map<String, Object> mapping = mapping("""
                {"path": "EventDetail/Update/After/Object", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}]}""");

        SnapshotChanges.apply(mapping, field -> TARGETS, Map.of("targetResources[].modifiedProperties",
                "newValue"));

        assertThat(String.valueOf(fieldsOf(mapping).get(1))).contains("old_key=newValue, new_key=oldValue");

        // A Data group (no snapshot), an Authorise's group, and XML input: nothing to do.
        final Map<String, Object> unchanged = mapping("""
                {"path": "EventDetail/Update/Data", "data_name": "targets", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Data", "data_name": "id", "field": "id"}]},
                {"path": "EventDetail/Authorise/User", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}]}""");
        assertThat(SnapshotChanges.apply(unchanged, field -> TARGETS, Map.of())).isEmpty();
        final Map<String, Object> xml = mapping("""
                {"path": "EventDetail/Update/After/Object", "for_each": {"array": "targetResources"},
                 "fields": [{"path": "Id", "field": "id"}]}""");
        xml.put("input", "xml");
        assertThat(SnapshotChanges.apply(xml, field -> TARGETS, Map.of())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(final String fields) {
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
}
