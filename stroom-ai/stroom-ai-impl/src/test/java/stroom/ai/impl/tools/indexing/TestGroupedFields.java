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

import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.generation.FieldGroup;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.XsltStyle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The fields a translation's repeated group writes, in an index plan: one field of objects in Elasticsearch, fields
/// of several values in Lucene, each saying what that means for a query.
class TestGroupedFields {

    // Each recipient one Destination/User with its own Id and Domain.
    private static final String RECIPIENTS = """
            {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
             "as": "recipient",
             "fields": [{"path": "Id", "item": true, "transform": "strip_domain"},
                        {"path": "Domain", "item": true, "transform": "domain"}]}""";
    private static final String EVENTS = """
            <Events xmlns="event-logging:3">
              <Event StreamId="7" EventId="1">
                <EventTime><TimeCreated>2026-10-01T10:00:00.000Z</TimeCreated></EventTime>
                <EventDetail><Send><Destination>
                  <User><Id>bob</Id><Domain>example.org</Domain></User>
                  <User><Id>carol</Id><Domain>example.com</Domain></User>
                </Destination></Send></EventDetail>
              </Event>
            </Events>""";

    private static List<FieldGroup.Leaf> leaves(final String... entries) {
        final List<FieldMapping> mappings = new ArrayList<>();
        for (final String entry : entries) {
            mappings.add(FieldMapping.fromJson(IndexingFixtures.json(entry)));
        }
        return FieldGroup.leaves(mappings);
    }

    private static FieldPlan plan(final String backend, final List<PlannedField> extra) {
        final List<PlannedField> fields = new ArrayList<>(List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated")));
        fields.addAll(extra);
        return new FieldPlan(backend, "mail", "@timestamp", fields, List.of(), null, List.of(), List.of(), true,
                null, List.of(), XsltStyle.DEFAULT, Map.of());
    }

    private static List<PlannedField> recipientFields() {
        return List.of(
                new PlannedField("destination.user.id", "keyword", "EventDetail/Send/Destination/User/Id",
                        "The recipient's login.", null, true),
                PlannedField.of("source.ip", "ip", "EventSource/Device/IPAddress"),
                new PlannedField("destination.user.domain", "keyword", "EventDetail/Send/Destination/User/Domain",
                        "", null, true));
    }

    @Test
    void elasticsearchPlansAGroupsFieldsAsOneFieldOfObjects() {
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, recipientFields());

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan, leaves(RECIPIENTS));

        final List<PlannedField> fields = grouped.plan().fields();
        assertThat(fields).extracting(PlannedField::name)
                .containsExactly("StreamId", "EventId", "@timestamp", "destination.user", "source.ip");
        final PlannedField users = fields.get(3);
        assertThat(users.type()).isEqualTo(PlannedField.OBJECT);
        assertThat(users.source()).isEqualTo("EventDetail/Send/Destination/User");
        assertThat(users.items()).extracting(PlannedField::name, PlannedField::source, PlannedField::multiple)
                .containsExactly(tuple("id", "Id", false),
                        tuple("domain", "Domain", false));
        final String note = "Per item: one object for each recipient (each of `recipients` split on ','), mapped as "
                            + "object, not nested: a query on two of an item's fields can match values from "
                            + "different items.";
        assertThat(users.description()).isEqualTo(note);
        assertThat(users.items().getFirst().description()).isEqualTo("The recipient's login. " + note);
        assertThat(users.items().get(1).description()).isEqualTo(note);
        assertThat(grouped.notes()).containsExactly("`destination.user`: the translation writes "
                                                    + "EventDetail/Send/Destination/User for each recipient (each of "
                                                    + "`recipients` split on ','), so `destination.user.id`, "
                                                    + "`destination.user.domain` are one object per item (mapped as "
                                                    + "object, not nested)");
        // The index's field names are as they were, and the plan stands.
        assertThat(grouped.plan().leafFields()).extracting(PlannedField::name)
                .contains("destination.user.id", "destination.user.domain");
        assertThat(grouped.plan().required()).isEmpty();
        // Each recipient an object of its own values.
        final String out = IndexingFixtures.compact(IndexingFixtures.transform(grouped.plan().xslt(), EVENTS));
        assertThat(out).contains("<array key=\"user\"><map><string key=\"id\">bob</string>"
                                 + "<string key=\"domain\">example.org</string></map>"
                                 + "<map><string key=\"id\">carol</string>"
                                 + "<string key=\"domain\">example.com</string></map></array>");
        // Its template maps it as an object, not nested.
        final Map<String, Object> template = PlanTemplates.elasticTemplate(grouped.plan(), "mail", null, false, null);
        assertThat(String.valueOf(template)).doesNotContain("nested")
                .contains("user={properties={id={type=keyword}, domain={type=keyword}}}");
    }

    @Test
    void luceneKeepsFieldsOfSeveralValuesSayingThePairingIsLost() {
        final FieldPlan plan = plan(FieldPlan.LUCENE, recipientFields());

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan, leaves(RECIPIENTS));

        final List<PlannedField> fields = grouped.plan().fields();
        assertThat(fields).extracting(PlannedField::name).containsExactly("StreamId", "EventId", "@timestamp",
                "destination.user.id", "source.ip", "destination.user.domain");
        assertThat(fields.get(3).multiple()).isTrue();
        assertThat(fields.get(3).description()).isEqualTo("The recipient's login. Per item: one value for each "
                                                          + "recipient (each of `recipients` split on ','), beside "
                                                          + "`destination.user.domain` of the same item; the pairing "
                                                          + "of an item's values is lost: a query on two of them can "
                                                          + "match values from different items.");
        assertThat(fields.get(5).description()).startsWith("Per item: one value for each recipient")
                .contains("beside `destination.user.id` of the same item");
        assertThat(fields.get(4)).isEqualTo(plan.fields().get(4));
        assertThat(grouped.plan().required()).isEmpty();
        assertThat(grouped.notes()).singleElement().asString()
                .startsWith("`destination.user.id`, `destination.user.domain`: the translation writes "
                            + "EventDetail/Send/Destination/User")
                .endsWith("which of an item's values go together is lost");
    }

    @Test
    void flatElasticsearchNamesAreFieldsOfValuesToo() {
        final FieldPlan plan = IndexingFixtures.withSubobjects(plan(FieldPlan.ELASTICSEARCH, recipientFields()),
                false);

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan, leaves(RECIPIENTS));

        assertThat(grouped.plan().fields()).noneMatch(PlannedField::isObjects);
        assertThat(grouped.plan().fields().get(3).description()).contains("the pairing of an item's values is lost");
    }

    @Test
    void namesSharingNoObjectAreFieldsOfValues() {
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, List.of(
                PlannedField.of("RecipientId", "keyword", "EventDetail/Send/Destination/User/Id"),
                PlannedField.of("RecipientDomain", "keyword", "EventDetail/Send/Destination/User/Domain")));

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan, leaves(RECIPIENTS));

        assertThat(grouped.plan().fields()).noneMatch(PlannedField::isObjects);
        assertThat(grouped.plan().fields().get(3).multiple()).isTrue();
    }

    @Test
    void anotherFieldUnderTheObjectsNameKeepsFieldsOfValues() {
        final List<PlannedField> fields = new ArrayList<>(recipientFields());
        fields.add(PlannedField.of("destination.user.name", "keyword", "EventDetail/Receive/Destination/User/Name"));

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan(FieldPlan.ELASTICSEARCH, fields),
                leaves(RECIPIENTS));

        assertThat(grouped.plan().fields()).noneMatch(PlannedField::isObjects);
    }

    @Test
    void aWildcardSourceKeepsItsWildcardAsTheObjectsSource() {
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, List.of(
                PlannedField.of("destination.user.id", "keyword", "EventDetail/*/Destination/User/Id"),
                PlannedField.of("destination.user.domain", "keyword", "EventDetail/*/Destination/User/Domain")));

        final PlannedField users = GroupedFields.apply(plan, leaves(RECIPIENTS)).plan().fields().get(3);

        assertThat(users.source()).isEqualTo("EventDetail/*/Destination/User");
        assertThat(users.items()).extracting(PlannedField::source).containsExactly("Id", "Domain");
    }

    @Test
    void aGroupInsideTheGroupIsSeveralValuesOfEachObject() {
        final String attachments = """
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient",
                 "fields": [{"path": "Id", "item": true},
                            {"path": "Location", "for_each": {"split": "rooms", "on": ";", "scope": "record"},
                             "fields": [{"path": "Room", "item": true}]}]}""";
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, List.of(
                PlannedField.of("destination.user.id", "keyword", "EventDetail/Send/Destination/User/Id"),
                PlannedField.of("destination.user.room", "keyword",
                        "EventDetail/Send/Destination/User/Location/Room")));

        final PlannedField users = GroupedFields.apply(plan, leaves(attachments)).plan().fields().get(3);

        assertThat(users.items()).extracting(PlannedField::name, PlannedField::source, PlannedField::multiple)
                .containsExactly(tuple("id", "Id", false),
                        tuple("room", "Location/Room", true));
    }

    @Test
    void aDataGroupsDataAreOneObjectPerDataElement() {
        final String recipients = """
                {"path": "EventDetail/Send/Data", "data_name": "recipient", "for_each": {"array": "recipients"},
                 "fields": [{"path": "Data", "data_name": "id", "field": "id"},
                            {"path": "Data", "data_name": "domain", "field": "domain"}]}""";
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, List.of(
                PlannedField.of("recipient.id", "keyword",
                        "EventDetail/Send/Data[@Name='recipient']/Data[@Name='id']/@Value"),
                PlannedField.of("recipient.domain", "keyword",
                        "EventDetail/Send/Data[@Name='recipient']/Data[@Name='domain']/@Value")));

        final PlannedField recipient = GroupedFields.apply(plan, leaves(recipients)).plan().fields().get(3);

        assertThat(recipient.source()).isEqualTo("EventDetail/Send/Data[@Name='recipient']");
        assertThat(recipient.items()).extracting(PlannedField::source)
                .containsExactly("Data[@Name='id']/@Value", "Data[@Name='domain']/@Value");
    }

    @Test
    void aPlanWithoutGroupsIsAsItWas() {
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, recipientFields());
        final String plain = """
                {"path": "EventDetail/Send/Destination/User/Id", "field": "recipient"}""";

        final GroupedFields.Grouped grouped = GroupedFields.apply(plan, leaves(plain));

        assertThat(grouped.plan()).isEqualTo(plan);
        assertThat(grouped.notes()).isEmpty();
        assertThat(GroupedFields.apply(plan, List.of()).plan()).isEqualTo(plan);
        assertThat(GroupedFields.apply(plan, null).plan()).isEqualTo(plan);
    }

    @Test
    void oneFieldOfAGroupIsLeftAsItIs() {
        final FieldPlan plan = plan(FieldPlan.ELASTICSEARCH, List.of(
                PlannedField.of("destination.user.id", "keyword", "EventDetail/Send/Destination/User/Id")));

        assertThat(GroupedFields.apply(plan, leaves(RECIPIENTS)).plan()).isEqualTo(plan);
    }

    @Test
    void theDocumentationCarriesTheNotesAfterTheFieldsOwnWords() {
        final GroupedFields.Grouped elastic = GroupedFields.apply(plan(FieldPlan.ELASTICSEARCH, recipientFields()),
                leaves(RECIPIENTS));
        final GroupedFields.Grouped lucene = GroupedFields.apply(plan(FieldPlan.LUCENE, recipientFields()),
                leaves(RECIPIENTS));

        final String elasticDoc = IndexFieldDocs.indexFieldMappingMarkdown(elastic.plan(), null, null, null, null,
                null, FieldDocs.COMPACT, null);
        final String luceneDoc = IndexFieldDocs.indexFieldMappingMarkdown(lucene.plan(), null, null, null, null,
                null, FieldDocs.COMPACT, null);

        assertThat(elasticDoc).contains("`destination.user.id`", "The recipient's login. Per item: one object for "
                                                                 + "each recipient",
                "mapped as object, not nested: a query on two of an item's fields can match values from different "
                + "items.");
        assertThat(luceneDoc).contains("The recipient's login. Per item: one value for each recipient",
                "the pairing of an item's values is lost");
    }

    @Test
    void theNoteIsReadApartFromTheFieldsOwnWords() {
        assertThat(GroupedFields.note("Login. Per item: one value.")).isEqualTo("Per item: one value.");
        assertThat(GroupedFields.withoutNote("Login. Per item: one value.")).isEqualTo("Login.");
        assertThat(GroupedFields.note("Login.")).isEmpty();
        assertThat(GroupedFields.withoutNote("Login.")).isEqualTo("Login.");
        assertThat(GroupedFields.note(null)).isEmpty();
        assertThat(GroupedFields.withoutNote(null)).isEmpty();
    }
}
