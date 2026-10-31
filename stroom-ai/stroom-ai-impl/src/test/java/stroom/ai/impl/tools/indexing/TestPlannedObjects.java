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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A field of an array of objects in an index plan: every user an event names, once each by login name (asked for by
/// the user).
class TestPlannedObjects {

    private static final String USERS = """
            {"name": "users", "type": "object", "source": "EventSource/User | EventDetail//User",
             "distinct_by": "Id",
             "items": [{"name": "login", "type": "keyword", "source": "Id"},
                       {"name": "email", "type": "keyword", "source": "EmailAddress"},
                       {"name": "organisation", "type": "keyword", "source": "UserDetails/Organisation"}]}""";
    // alice twice (as the source and a recipient), bob, and a recipient with no login.
    private static final String EVENTS = """
            <Events xmlns="event-logging:3">
              <Event StreamId="7" EventId="1">
                <EventTime><TimeCreated>2026-10-01T10:00:00.000Z</TimeCreated></EventTime>
                <EventSource><User><Id>alice</Id><EmailAddress>alice@example.org</EmailAddress>
                  <UserDetails><Organisation>Finance</Organisation></UserDetails></User></EventSource>
                <EventDetail><Send><Source><User><Id>alice</Id></User></Source>
                  <Destination><User><Id>bob</Id><EmailAddress>bob@example.org</EmailAddress></User>
                  <User><EmailAddress>nobody@example.org</EmailAddress></User></Destination></Send></EventDetail>
              </Event>
              <Event StreamId="7" EventId="2">
                <EventTime><TimeCreated>2026-10-01T10:01:00.000Z</TimeCreated></EventTime>
                <EventDetail><TypeId>none</TypeId></EventDetail>
              </Event>
            </Events>""";

    @Test
    void usersAreWrittenOnceEachByLoginInTheOrderFirstMetFromTheirFirstElement() {
        final String out = IndexingFixtures.compact(IndexingFixtures.transform(plan().xslt(), EVENTS));

        assertThat(out).contains("<array key=\"users\">"
                                 + "<map><string key=\"login\">alice</string>"
                                 + "<string key=\"email\">alice@example.org</string>"
                                 + "<string key=\"organisation\">Finance</string></map>"
                                 + "<map><string key=\"login\">bob</string>"
                                 + "<string key=\"email\">bob@example.org</string></map>"
                                 + "</array>");
        assertThat(out).doesNotContain("nobody@example.org");
        // An event naming no user has no users field, not an empty array.
        assertThat(out.substring(out.indexOf("<number key=\"EventId\">2</number>"))).doesNotContain("users");
    }

    @Test
    void withoutAKeyEveryElementIsAnObject() {
        final Map<String, Object> json = IndexingFixtures.json(USERS);
        json.remove("distinct_by");
        final String out = IndexingFixtures.compact(IndexingFixtures.transform(plan(PlannedField.fromJson(json))
                .xslt(), EVENTS));

        assertThat(out.split("<string key=\"login\">alice</string>", -1)).hasSize(3);
        assertThat(out).contains("nobody@example.org");
    }

    @Test
    void theTemplateAndSearchesSeeItsItemsAsFieldsUnderItsName() {
        final FieldPlan plan = plan();

        assertThat(plan.leafFields()).extracting(PlannedField::name)
                .contains("users.login", "users.email", "users.organisation")
                .doesNotContain("users");
        assertThat(plan.leafFields()).filteredOn(field -> field.name().equals("users.login")).singleElement()
                .satisfies(login -> {
                    assertThat(login.source()).isEqualTo("EventSource/User/Id | EventDetail//User/Id");
                    assertThat(login.multiple()).isTrue();
                });
        final String template = PyJson.dumps(plan.elasticTemplate("acme-v1", null, false, null));
        assertThat(template).contains("\"users\": {\"properties\": {\"login\": {\"type\": \"keyword\"}, \"email\": "
                                      + "{\"type\": \"keyword\"}, \"organisation\": {\"type\": \"keyword\"}}}");
        // Its paths count as mapped.
        assertThat(IndexingHelpers.populatedNotMapped(plan, Map.of("EventDetail/Send/Destination/User/Id", 50,
                "EventSource/User/EmailAddress", 100))).isEmpty();
    }

    @Test
    void itIsKeptAsJsonAndAPlanWithoutOneIsWrittenAsBefore() {
        final PlannedField users = PlannedField.fromJson(IndexingFixtures.json(USERS));

        assertThat(PlannedField.fromJson(users.toJson())).isEqualTo(users);
        assertThat(users.toJson()).containsEntry("distinct_by", "Id").containsKey("items");
        assertThat(PlannedField.of("user.name", "keyword", "EventSource/User/Id").toJson())
                .doesNotContainKeys("items", "distinct_by");
    }

    @Test
    void itemsAndObjectGoTogetherAndAKeyNeedsItems() {
        final Map<String, Object> noItems = IndexingFixtures.json(USERS);
        noItems.remove("items");
        noItems.remove("distinct_by");
        assertThatThrownBy(() -> PlannedField.fromJson(noItems)).isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("a field of type object has items");
        final Map<String, Object> keyword = IndexingFixtures.json(USERS);
        keyword.put("type", "keyword");
        assertThatThrownBy(() -> PlannedField.fromJson(keyword)).isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("a field of type object has items");
        final Map<String, Object> keyOnly = IndexingFixtures.json(
                "{\"name\": \"u\", \"type\": \"keyword\", \"source\": \"EventSource/User/Id\", "
                + "\"distinct_by\": \".\"}");
        assertThatThrownBy(() -> PlannedField.fromJson(keyOnly)).isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("distinct_by is for a field with items");
    }

    @Test
    void aLuceneIndexHoldsNoObjects() {
        final FieldPlan lucene = IndexingFixtures.withBackend(plan(), "lucene");

        assertThat(lucene.required()).anySatisfy(problem -> assertThat(problem)
                .startsWith("['users']: a Lucene index holds no objects"));
        assertThat(plan().required()).isEmpty();
    }

    @Test
    void aUnionsPathsAreEachMatched() {
        assertThat(FieldPlans.branches("EventSource/User | EventDetail//User")).containsExactly("EventSource/User",
                "EventDetail//User");
        assertThat(FieldPlans.branches("Data[@Name='a|b']/@Value")).containsExactly("Data[@Name='a|b']/@Value");
        assertThat(FieldPlans.sourceMatches("EventSource/User/Id | EventDetail//User/Id",
                "EventDetail/Send/Destination/User/Id")).isTrue();
        assertThat(FieldPlans.sourceMatches("EventSource/User/Id | EventDetail//User/Id", "EventSource/Device/Id"))
                .isFalse();
    }

    private static FieldPlan plan() {
        return plan(PlannedField.fromJson(IndexingFixtures.json(USERS)));
    }

    private static FieldPlan plan(final PlannedField users) {
        final List<PlannedField> fields = new ArrayList<>(List.of(PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                // Read often, Id goes in a variable of the Event's: never the items' Id, read from each user.
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("user.id", "keyword", "EventSource/User/Id")));
        fields.add(users);
        return FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", fields);
    }
}
