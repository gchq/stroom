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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.indexing.FeedIndexes.Clash;
import stroom.ai.impl.tools.indexing.FeedIndexes.Decided;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An aggregated feed's indexes as its registry keeps them: one per output feed, shared by its groups, its fields the
/// union of theirs, a field of another type or meaning a clash, and a promoted build's fields added.
class TestFeedIndexes {

    private static final String FEED = "PEGA";
    private static final String OWN_FEED = "PEGA-INTEGRATION-EVENTS";
    private static final String INDEX = "pega-events";
    private static final Decided FIRST = new Decided("user", "2026-11-13T10:00:00.000Z", "pega-1");
    private static final Decided LATER = new Decided("user", "2026-11-13T11:00:00.000Z", "pega-2");

    @Test
    void theOutputFeedsAreTheFeedsOwnAndEachGroupsOwn() {
        final Map<String, Object> registry = registry();

        assertThat(FeedIndexes.outputFeeds(registry)).containsExactly(FEED, OWN_FEED);
        assertThat(FeedIndexes.groupsTo(registry, FEED)).containsExactly("PEGA security", "PEGA mail");
        assertThat(FeedIndexes.groupsTo(registry, OWN_FEED)).containsExactly("PEGA integration");
    }

    @Test
    void theSharedDecisionsAreRecordedOnceAndANewBackendClearsTheNaming() {
        final Map<String, Object> registry = registry();

        FeedIndexes.settleIndexing(registry, FieldPlan.LUCENE, null, FIRST);
        FeedIndexes.settleIndexing(registry, null, Map.of("convention", "stroom-flat"), FIRST);

        assertThat(FeedIndexes.indexing(registry)).containsEntry("backend", FieldPlan.LUCENE)
                .containsEntry("naming", Map.of("convention", "stroom-flat"));
        assertThat(JsonValues.mapOrEmpty(FeedIndexes.indexing(registry).get("decided")))
                .containsEntry("build", "pega-1");

        FeedIndexes.settleIndexing(registry, FieldPlan.ELASTICSEARCH, null, LATER);

        assertThat(FeedIndexes.indexing(registry)).containsEntry("backend", FieldPlan.ELASTICSEARCH)
                .doesNotContainKey("naming");
    }

    @Test
    void aPromotedPlanIsRecordedAsItsOutputFeedsIndexWithTheSharedDecisions() {
        final Map<String, Object> registry = registry();

        final List<String> added = FeedIndexes.record(registry, FEED, firstPlan(),
                Map.of("type", "Index", "uuid", "u-1", "name", INDEX), FIRST);

        assertThat(added).containsExactly("StreamId", "EventId", "EventTime", "UserId", "TypeId");
        final Map<String, Object> entry = FeedIndexes.index(registry, FEED);
        assertThat(entry).containsEntry("index", INDEX).containsEntry("backend", FieldPlan.LUCENE);
        assertThat(JsonValues.strings(entry.get("groups"))).containsExactly("PEGA security", "PEGA mail");
        assertThat(JsonValues.mapOrEmpty(entry.get("added_by"))).containsEntry("UserId", "pega-1");
        assertThat(FeedIndexes.index(registry, OWN_FEED)).isEmpty();
        // The feed's shared decisions, from the plan where none were made in a form.
        assertThat(FeedIndexes.indexing(registry)).containsEntry("backend", FieldPlan.LUCENE)
                .containsEntry("naming", Map.of("convention", "stroom-flat"));
        assertThat(FeedIndexes.outputFeedOfIndex(registry, INDEX)).contains(FEED);
        assertThat(FeedIndexes.outputFeedOfIndex(registry, "other")).isEmpty();
    }

    @Test
    void aLaterPlanInheritsTheFieldsOfTheIndexItLacks() {
        final Map<String, Object> registry = recorded();
        final FieldPlan later = plan(INDEX, field("StreamId", "id", "@StreamId"), field("EventId", "id", "@EventId"),
                field("Direction", "keyword", "EventDetail/TypeId"));

        final FieldPlan union = FeedIndexes.withInherited(later, FeedIndexes.index(registry, FEED));

        assertThat(union.fields()).extracting(PlannedField::name)
                .containsExactly("StreamId", "EventId", "Direction", "EventTime", "UserId", "TypeId");
        assertThat(FeedIndexes.clashes(union, FeedIndexes.index(registry, FEED))).isEmpty();
        assertThat(FeedIndexes.newFields(union, FeedIndexes.index(registry, FEED))).extracting(PlannedField::name)
                .containsExactly("Direction");
    }

    @Test
    void aFieldOfAnotherTypeIsATypeClashSaidForTheBackend() {
        final Map<String, Object> entry = FeedIndexes.index(recorded(), FEED);
        final FieldPlan later = FeedIndexes.withInherited(plan(INDEX, field("TypeId", "long", "EventDetail/TypeId")),
                entry);

        final List<Clash> clashes = FeedIndexes.clashes(later, entry);

        assertThat(clashes).singleElement().satisfies(clash -> {
            assertThat(clash.kind()).isEqualTo(FeedIndexes.TYPE_CLASH);
            assertThat(clash.choices()).containsExactly(FeedIndexes.RENAME, FeedIndexes.NEW_VERSION);
            assertThat(clash.question()).isEqualTo("Field TypeId is long here but keyword in index pega-events. "
                                                   + "Which?");
            assertThat(clash.said()).contains("added by build pega-1", "Documents already indexed keep the old type");
            assertThat(clash.options("pega_integration")).containsValues(FeedIndexes.RENAME, FeedIndexes.NEW_VERSION)
                    .containsKey("Rename: rename this build's field to TypeId_pega_integration")
                    .containsKey("New index version: pega-events-v2, from now on");
        });
    }

    @Test
    void anElasticsearchTypeClashSaysItCantChangeInPlace() {
        final Map<String, Object> registry = registry();
        FeedIndexes.record(registry, FEED, firstPlan().withFields(List.of(field("TypeId", "keyword",
                "EventDetail/TypeId"))), null, FIRST);
        final Map<String, Object> entry = new LinkedHashMap<>(FeedIndexes.index(registry, FEED));
        entry.put("backend", FieldPlan.ELASTICSEARCH);

        final List<Clash> clashes = FeedIndexes.clashes(plan(INDEX, field("TypeId", "long", "EventDetail/TypeId")),
                entry);

        assertThat(clashes).singleElement().satisfies(clash -> assertThat(clash.said())
                .contains("Elasticsearch can't change a field's type in an index that has it"));
    }

    @Test
    void aFieldReadFromAnotherPathOrDescribedOtherwiseIsAMeaningClash() {
        final Map<String, Object> entry = FeedIndexes.index(recorded(), FEED);

        final List<Clash> byPath = FeedIndexes.clashes(plan(INDEX, field("UserId", "keyword",
                "EventDetail/Send/Source/User/Id")), entry);
        final List<Clash> byDescription = FeedIndexes.clashes(plan(INDEX, new PlannedField("UserId", "keyword",
                "EventSource/User/Id", "The operator acting.", null, false)), FeedIndexes.index(described(), FEED));

        assertThat(byPath).singleElement().satisfies(clash -> {
            assertThat(clash.kind()).isEqualTo(FeedIndexes.MEANING_CLASH);
            assertThat(clash.choices()).containsExactly(FeedIndexes.SAME_MEANING, FeedIndexes.RENAME);
            assertThat(clash.question()).contains("reads EventDetail/Send/Source/User/Id", "reads EventSource/User/Id",
                    "Same meaning?");
        });
        assertThat(byDescription).singleElement().satisfies(clash -> assertThat(clash.question())
                .contains("described as 'The operator acting.'", "described as 'The user logging on.'"));
    }

    @Test
    void theSameMeaningIsNoClashWhateverTheCaseOrAFullStop() {
        final Map<String, Object> entry = FeedIndexes.index(described(), FEED);

        assertThat(FeedIndexes.clashes(plan(INDEX, new PlannedField("UserId", "keyword", "EventSource/User/Id",
                "the user  logging on", null, false)), entry)).isEmpty();
        // Reading more than the index's field (both groups' paths) is no clash.
        assertThat(FeedIndexes.clashes(plan(INDEX, field("UserId", "keyword",
                "EventSource/User/Id | EventDetail/Send/Source/User/Id")), FeedIndexes.index(recorded(), FEED)))
                .isEmpty();
        // The ids and the time mean the same in every group.
        assertThat(FeedIndexes.clashes(plan(INDEX, field("EventTime", "date", "EventDetail/Time")),
                FeedIndexes.index(recorded(), FEED))).isEmpty();
    }

    @Test
    void anotherIndexHasNoClashes() {
        final Map<String, Object> entry = FeedIndexes.index(recorded(), FEED);

        assertThat(FeedIndexes.clashes(plan("pega-events-v2", field("TypeId", "long", "EventDetail/TypeId")),
                entry)).isEmpty();
        assertThat(FeedIndexes.clashes(plan(INDEX, field("TypeId", "long", "EventDetail/TypeId")), Map.of()))
                .isEmpty();
    }

    @Test
    void eachAnswerResolvesItsClash() {
        final Map<String, Object> entry = FeedIndexes.index(recorded(), FEED);
        final FieldPlan typed = FeedIndexes.withInherited(plan(INDEX, field("TypeId", "long", "EventDetail/TypeId")),
                entry);
        final Clash typeClash = FeedIndexes.clashes(typed, entry).getFirst();
        final FieldPlan meant = FeedIndexes.withInherited(plan(INDEX, field("UserId", "keyword",
                "EventDetail/Send/Source/User/Id")), entry);
        final Clash meaningClash = FeedIndexes.clashes(meant, entry).getFirst();

        final FieldPlan renamed = FeedIndexes.resolved(typed, entry, typeClash, FeedIndexes.RENAME, "pega_mail");
        final FieldPlan versioned = FeedIndexes.resolved(typed, entry, typeClash, FeedIndexes.NEW_VERSION, null);
        final FieldPlan same = FeedIndexes.resolved(meant, entry, meaningClash, FeedIndexes.SAME_MEANING, null);

        // Renamed: the build's field of its own, the index's own inherited again.
        assertThat(renamed.fields()).extracting(PlannedField::name).contains("TypeId_pega_mail", "TypeId");
        assertThat(renamed.fields()).filteredOn(field -> "TypeId".equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.type()).isEqualTo("keyword"));
        assertThat(FeedIndexes.clashes(renamed, entry)).isEmpty();
        // A new version: every group's fields, the build's type, nothing clashing.
        assertThat(versioned.indexName()).isEqualTo("pega-events-v2");
        assertThat(versioned.fields()).extracting(PlannedField::name).contains("TypeId", "UserId", "EventTime");
        assertThat(versioned.fields()).filteredOn(field -> "TypeId".equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.type()).isEqualTo("long"));
        assertThat(FeedIndexes.clashes(versioned, entry)).isEmpty();
        // The same meaning: one field, read from both.
        assertThat(same.fields()).filteredOn(field -> "UserId".equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.source())
                        .isEqualTo("EventSource/User/Id | EventDetail/Send/Source/User/Id"));
        assertThat(FeedIndexes.clashes(same, entry)).isEmpty();
        assertThatThrownBy(() -> FeedIndexes.resolved(meant, entry, meaningClash, FeedIndexes.NEW_VERSION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPromotedLaterBuildAddsItsFieldsForwardOnly() {
        final Map<String, Object> registry = recorded();
        final Map<String, Object> entry = FeedIndexes.index(registry, FEED);
        final FieldPlan later = FeedIndexes.resolved(FeedIndexes.withInherited(plan(INDEX, field("UserId", "keyword",
                        "EventDetail/Send/Source/User/Id"), field("Direction", "keyword", "EventDetail/TypeId")),
                entry), entry, FeedIndexes.clashes(FeedIndexes.withInherited(plan(INDEX, field("UserId", "keyword",
                "EventDetail/Send/Source/User/Id")), entry), entry).getFirst(), FeedIndexes.SAME_MEANING, null);

        final List<String> added = FeedIndexes.record(registry, FEED, later, null, LATER);

        assertThat(added).containsExactly("Direction");
        final Map<String, Object> after = FeedIndexes.index(registry, FEED);
        assertThat(JsonValues.mapOrEmpty(after.get("index_doc"))).containsEntry("uuid", "u-1");
        assertThat(JsonValues.mapOrEmpty(after.get("added_by"))).containsEntry("UserId", "pega-1")
                .containsEntry("Direction", "pega-2");
        assertThat(FeedIndexes.recordedFields(after)).filteredOn(field -> "UserId".equals(field.name()))
                .singleElement()
                .satisfies(field -> assertThat(field.source()).contains("EventDetail/Send/Source/User/Id"));
    }

    @Test
    void aNewVersionReplacesTheIndexRecorded() {
        final Map<String, Object> registry = recorded();

        final List<String> added = FeedIndexes.record(registry, FEED, plan("pega-events-v2", field("TypeId", "long",
                "EventDetail/TypeId")), null, LATER);

        assertThat(added).containsExactly("TypeId");
        assertThat(FeedIndexes.index(registry, FEED)).containsEntry("index", "pega-events-v2")
                .containsEntry("replaces", INDEX)
                .doesNotContainKey("index_doc");
    }

    @Test
    void theNextVersionCountsOn() {
        assertThat(FeedIndexes.nextVersion("acme")).isEqualTo("acme-v2");
        assertThat(FeedIndexes.nextVersion("acme-v2")).isEqualTo("acme-v3");
        assertThat(FeedIndexes.nextVersion("acme_V9")).isEqualTo("acme_V10");
        assertThat(FeedIndexes.renamed("TypeId", "")).isEqualTo("TypeId_2");
    }

    @Test
    void theRegistrySaysItsIndexes() {
        final Map<String, Object> registry = recorded();

        final String text = SourceTypeRegistry.render(registry);

        assertThat(text).contains("## Indexes", "Shared by every build of the feed: backend lucene, fields named by "
                                                + "the stroom-flat convention",
                "* Output feed `PEGA`: index `pega-events` (lucene), 5 field(s), for group(s) PEGA security, PEGA "
                + "mail.");
        assertThat(SourceTypeRegistry.read(text)).containsKey(FeedIndexes.INDEXES);
    }

    // --- fixtures ---

    private static Map<String, Object> registry() {
        final List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("PegaSecurityEvent", "PEGA security", null));
        rows.add(row("PegaMail", "PEGA mail", null));
        rows.add(row("PegaIntegration", "PEGA integration", OWN_FEED));
        final Map<String, Object> parked = new LinkedHashMap<>();
        parked.put("name", "PegaDB");
        parked.put("status", "parked");
        rows.add(parked);
        final Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("feed", FEED);
        registry.put("field", "sourcetype");
        registry.put("rows", rows);
        return registry;
    }

    private static Map<String, Object> row(final String name, final String group, final String outputFeed) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", "value");
        row.put("value", name);
        row.put("status", "translated");
        row.put("group", group);
        if (outputFeed != null) {
            row.put("output_feed", outputFeed);
        }
        return row;
    }

    // The registry once the first build's plan is promoted.
    private static Map<String, Object> recorded() {
        final Map<String, Object> registry = registry();
        FeedIndexes.record(registry, FEED, firstPlan(), Map.of("type", "Index", "uuid", "u-1", "name", INDEX), FIRST);
        return registry;
    }

    // As recorded(), its user field described.
    private static Map<String, Object> described() {
        final Map<String, Object> registry = registry();
        final List<PlannedField> fields = new ArrayList<>(firstPlan().fields());
        fields.replaceAll(field -> "UserId".equals(field.name())
                ? new PlannedField("UserId", "keyword", "EventSource/User/Id", "The user logging on.", null, false)
                : field);
        FeedIndexes.record(registry, FEED, firstPlan().withFields(fields), null, FIRST);
        return registry;
    }

    private static FieldPlan firstPlan() {
        return plan(INDEX, field("StreamId", "id", "@StreamId"), field("EventId", "id", "@EventId"),
                field("EventTime", "date", "EventTime/TimeCreated"), field("UserId", "keyword", "EventSource/User/Id"),
                field("TypeId", "keyword", "EventDetail/TypeId"));
    }

    private static FieldPlan plan(final String index, final PlannedField... fields) {
        return new FieldPlan(FieldPlan.LUCENE, index, "EventTime", List.of(fields), List.of(), null, List.of(),
                List.of(), true, "stroom-flat", List.of(), null, Map.of(), null);
    }

    private static PlannedField field(final String name, final String type, final String source) {
        return PlannedField.of(name, type, source);
    }
}
