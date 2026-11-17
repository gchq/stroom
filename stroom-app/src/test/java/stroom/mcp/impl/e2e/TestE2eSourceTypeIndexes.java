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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexShardService;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's indexes through the REAL tools on a real Stroom, on the synthetic PEGA sample under
/// `aggregated/`:
///
/// * each source type group has an index of its own, even where two groups' Events go into the same feed: each
///   group's indexing pipeline's processor filter selects only its group's pipeline's Events, so its index holds its
///   group's documents alone (decided 2026-11-14); the first build's backend and naming are the feed's shared
///   decisions, kept in its registry and inherited by the next build (a change refused);
/// * another group's index only at the user's word (`share_index_with`): refused without it, in the draft and at
///   promotion; shared, the plan is the union of the groups' fields; a field of another type and a field of another
///   meaning are held at promotion, then resolved in one form (a field of another name; one field read from both);
///   promotion adds the build's new fields to the index (and to the Lucene index doc), on Lucene and Elasticsearch
///   alike;
/// * a group with an output feed of its own (a later build's: one source type group a build) has an index of its own,
///   the first group's index refused for its Events.
@SuppressWarnings("unchecked")
class TestE2eSourceTypeIndexes extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String STROOM_FLAT = "Stroom flat convention";

    @Inject
    private IndexFieldService indexFieldService;
    @Inject
    private IndexShardService indexShardService;
    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @ParameterizedTest
    @ValueSource(strings = {"lucene", "elasticsearch"})
    void aGroupSharesAnotherGroupsIndexOnlyAtTheUsersWordItsClashesHeldUntilTheUserChooses(final String backend) {
        final String tag = backend.substring(0, 1).toUpperCase(Locale.ROOT);
        final String feed = "E2E-PEGA-IDX-" + tag;
        final String folder = "E2E Feeds/PegaIdx" + tag;
        final String index = "e2e-pega-idx-" + tag.toLowerCase(Locale.ROOT);
        final boolean lucene = "lucene".equals(backend);
        createFeed("pega-idx-src-" + tag, feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-idx-src-" + tag, Map.of("Feed", folder));

        // The first build: the security group, its Events indexed; the backend and naming chosen with the user.
        final String first = "pega-idx-1" + tag;
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaSecurityEvent"), null));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed, SECURITY);
        group(first, feed, SECURITY, "PegaSecurityEvent", TestE2eSourceTypeGroups.securityMapping(), stream);
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", first, "group", SECURITY,
                "template_uuid", template)));
        final List<Long> firstEvents = events(pipeline, stream);
        client.answeringWith(choosing(STROOM_FLAT));
        final Map<String, Object> conventions = json("get_field_conventions", lucene
                ? args()
                : args("backend", backend));
        assertThat(conventions).as("chosen in a form: %s", conventions).containsEntry("status", "chosen");
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> firstDraft = draft(backend, index, firstEvents, null, null);
        assertThat(map(firstDraft, "feed_index")).containsEntry("output_feed", feed).containsEntry("group", SECURITY);
        final Map<String, Object> firstPlan = map(firstDraft, "plan");
        if (lucene) {
            agreed("create_index_doc", args("build", first, "backend", backend, "name", index, "time_field",
                    firstPlan.get("time_field")));
        }
        json("save_xslt", args("build", first, "name", index + "-XSLT", "index_plan", firstPlan));
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder));
        final Map<String, Object> afterFirst = registry(folder, feed);
        final Map<String, Object> firstIndex = FeedIndexes.indexOfGroup(afterFirst, SECURITY);
        assertThat(firstIndex).containsEntry("index", index).containsEntry("backend", backend);
        assertThat(FeedIndexes.groupsOf(firstIndex)).containsExactly(SECURITY);
        assertThat((List<String>) firstIndex.get("output_feeds")).containsExactly(feed);
        assertThat(FeedIndexes.recordedFields(firstIndex)).hasSameSizeAs(list(firstPlan, "fields"));
        assertThat(FeedIndexes.indexing(afterFirst)).containsEntry("backend", backend)
                .containsEntry("naming", Map.of("convention", "stroom-flat"));
        if (lucene) {
            assertThat(map(firstIndex, "index_doc")).containsEntry("type", LuceneIndexDoc.TYPE);
        }

        // A later build: the integration group, its Events into the feed's, by a pipeline of its own.
        final String later = "pega-idx-2" + tag;
        json("start_build", args("build", later, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaIntegration"), null));
        json("survey_feed", args("feed", feed, "build", later));
        recordSourceTypes(later, feed, INTEGRATION);
        group(later, feed, INTEGRATION, "PegaIntegration", TestE2eSourceTypeGroups.integrationMapping(), stream);
        final String integrationPipeline = uuidOf(agreed("create_pipeline", args("build", later, "group",
                INTEGRATION, "template_uuid", template)));
        final List<Long> laterEvents = events(integrationPipeline, stream);

        // The backend and naming are inherited from the registry: nothing asked, a change refused.
        client.answeringWith(McpTestClient::agree);
        final int formsBefore = client.forms().size();
        final Map<String, Object> inherited = json("get_field_conventions", args());
        dump("get_field_conventions inherited", inherited);
        assertThat(inherited).containsEntry("status", "chosen").containsEntry("backend", backend);
        assertThat(String.valueOf(inherited.get("settled"))).contains(feed + SourceTypeRegistry.SUFFIX,
                "shared decisions");
        // An index of its own for the build's group, named like the first's with the group in it.
        final String ownName = FeedIndexes.groupIndexName(index, List.of(SECURITY), INTEGRATION);
        assertThat(String.valueOf(inherited.get("index_name"))).contains("an index of its own",
                "index_name=" + ownName, "share_index_with");
        assertThat(formsSince(formsBefore)).isEmpty();
        final String other = lucene
                ? "elasticsearch"
                : "lucene";
        assertThat(refusal("get_field_conventions", args("backend", other))).contains(
                feed + SourceTypeRegistry.SUFFIX, "change_shared");
        // The security group's index, not asked to share: refused, naming a name of the group's own.
        assertThat(refusal("draft_index_mapping", args("backend", backend, "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", laterEvents))).contains("is the index of group(s) " + SECURITY,
                "an index of its own", "'" + ownName + "', say", "share_index_with='" + SECURITY + "'");
        assertThat(refusal("draft_index_mapping", args("backend", backend, "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", laterEvents, "share_index_with", "PEGA nothing")))
                .contains("names no index of feed '" + feed + "'", index + " (group(s) " + SECURITY + ")");

        // A plan naming the security group's index, its fields clashing, saved as an agent might: promotion is
        // refused, the index not shared at the user's word.
        final String typeField = plannedName(firstPlan, "EventDetail/TypeId");
        final String hostField = plannedName(firstPlan, "EventSource/Device/HostName");
        final String hostType = plannedType(firstPlan, hostField);
        final Map<String, Object> clashing = new LinkedHashMap<>(firstPlan);
        final List<Map<String, Object>> clashingFields = new ArrayList<>();
        for (final Map<String, Object> field : list(firstPlan, "fields")) {
            final Map<String, Object> changed = new LinkedHashMap<>(field);
            if (typeField.equals(field.get("name"))) {
                changed.put("type", "long");
            } else if (hostField.equals(field.get("name"))) {
                changed.put("source", "EventSource/Device/IPAddress");
            }
            clashingFields.add(changed);
        }
        clashing.put("fields", clashingFields);
        final String laterXslt = uuidOf(json("save_xslt", args("build", later, "name", index + "-XSLT-2",
                "index_plan", clashing)));
        final Map<String, Object> destinations = Map.of("XSLT", folder, "Pipeline", folder);
        final String unshared = refusal("promote_build", args("build", later, "destinations", destinations));
        dump("promote_build unshared", unshared);
        assertThat(unshared).contains("Not promoted", "is the index of group(s) " + SECURITY,
                "not of group '" + INTEGRATION + "'", "share_index_with");

        // The user asks for one index with the security group's Events. Drafted with share_index_with: the union of
        // the groups' fields (the security group's read from its XSLT through the registry, its pipeline not this
        // build's), the clashes put to the user in one form.
        final int clashFormsBefore = client.forms().size();
        client.answeringWith(resolving());
        final Map<String, Object> laterDraft = draft(backend, index, laterEvents, List.of(
                Map.of("name", typeField, "type", "long", "source", "EventDetail/TypeId"),
                Map.of("name", hostField, "type", hostType, "source", "EventSource/Device/IPAddress")), SECURITY);
        client.answeringWith(McpTestClient::agree);
        final List<ElicitFormRequest> clashForms = formsSince(clashFormsBefore).stream()
                .filter(form -> form.message().contains("clash with index " + index))
                .toList();
        assertThat(clashForms).hasSize(1);
        assertThat(clashForms.getFirst().message()).contains("Field " + typeField, "Field " + hostField)
                .doesNotContain("**", "|", "`");
        assertThat(((Map<?, ?>) clashForms.getFirst().requestedSchema().get("properties"))).hasSize(2);
        final Map<String, Object> feedIndex = map(laterDraft, "feed_index");
        dump("draft_index_mapping later", feedIndex);
        assertThat(feedIndex).containsEntry("output_feed", feed).containsEntry("index", index)
                .containsEntry("group", INTEGRATION)
                .containsEntry("shared_with", List.of(SECURITY));
        assertThat((List<String>) feedIndex.get("groups")).containsExactlyInAnyOrder(SECURITY, INTEGRATION);
        assertThat((List<?>) feedIndex.get("clashes_resolved")).hasSize(2);
        final Map<String, Object> laterPlan = map(laterDraft, "plan");
        final String renamed = typeField + "_pega_integration";
        assertThat(plannedType(laterPlan, renamed)).isEqualTo("long");
        assertThat(plannedType(laterPlan, typeField)).isEqualTo(plannedType(firstPlan, typeField));
        assertThat(plannedSource(laterPlan, hostField)).contains("EventSource/Device/HostName",
                "EventSource/Device/IPAddress");
        // Every field of the index is in the plan: the groups' fields together.
        assertThat(list(laterPlan, "fields").stream().map(field -> field.get("name")).toList())
                .containsAll(list(firstPlan, "fields").stream().map(field -> field.get("name")).toList());

        // Shared now, the clashing plan saved again as an agent might: promotion is held until the user chooses.
        json("save_xslt", args("build", later, "uuid", laterXslt, "index_plan", clashing));
        final String held = refusal("promote_build", args("build", later, "destinations", destinations));
        dump("promote_build held", held);
        assertThat(held).contains("Not promoted: held", "Field " + typeField + ": long", "Field " + hostField,
                "draft_index_mapping");
        assertThat(held).contains(lucene
                ? "Documents already indexed keep the old type"
                : "Elasticsearch can't change a field's type");

        // Saved again from the resolved plan: promotion adds its new fields to the shared index.
        json("save_xslt", args("build", later, "uuid", laterXslt, "index_plan", laterPlan));
        final Map<String, Object> review = json("promote_build", args("build", later, "destinations",
                destinations));
        assertThat(review).containsEntry("status", "needs_review");
        assertThat(String.valueOf(review.get("index_changes"))).contains("index " + index, "adds", renamed,
                "forward only");
        promote(later, Map.of("XSLT", folder, "Pipeline", folder));
        final Map<String, Object> afterLater = registry(folder, feed);
        final Map<String, Object> laterIndex = FeedIndexes.indexOfGroup(afterLater, INTEGRATION);
        assertThat(laterIndex).containsEntry("index", index);
        assertThat(FeedIndexes.groupsOf(laterIndex)).containsExactlyInAnyOrder(SECURITY, INTEGRATION);
        assertThat(FeedIndexes.indexes(afterLater)).hasSize(1);
        assertThat(FeedIndexes.recordedFields(laterIndex)).anySatisfy(field -> {
            assertThat(field.name()).isEqualTo(renamed);
            assertThat(field.type()).isEqualTo("long");
        });
        assertThat(map(laterIndex, "added_by")).containsEntry(renamed, later).containsEntry(typeField, first);
        if (lucene) {
            final Map<String, Object> doc = map(laterIndex, "index_doc");
            assertThat(luceneFields(new DocRef(LuceneIndexDoc.TYPE, (String) doc.get("uuid")))).contains(renamed,
                    typeField);
        }
    }

    @Test
    void aGroupWithAnOutputFeedOfItsOwnHasAnIndexOfItsOwn() {
        final String feed = "E2E-PEGA-IDX-OWN";
        final String folder = "E2E Feeds/PegaIdxOwn";
        final String index = "e2e-pega-idx-own";
        createFeed("pega-idx-own-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-idx-own-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");

        // The first build: the security group, its Events into the feed's, indexed by the feed's index. One source
        // type group a build.
        final String first = "pega-idx-own-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(ownFeedFor(List.of("PegaSecurityEvent"), INTEGRATION));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed, SECURITY);
        final Map<String, Object> security = group(first, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        assertThat(security).doesNotContainKey("output_feed");
        final String main = uuidOf(agreed("create_pipeline", args("build", first, "group", SECURITY,
                "template_uuid", template)));
        final List<Long> mainEvents = events(main, stream);
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> mainDraft = draft("lucene", index, mainEvents, null, null);
        assertThat(map(mainDraft, "feed_index")).containsEntry("output_feed", feed);
        assertThat((List<String>) map(mainDraft, "feed_index").get("groups")).containsExactly(SECURITY);
        json("save_xslt", args("build", first, "name", index + "-XSLT", "index_plan", map(mainDraft, "plan")));
        promote(first, Map.of("Pipeline", folder, "XSLT", folder));

        // The second build: the integration group, its Events into an output feed of its own.
        final String second = "pega-idx-own-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        client.answeringWith(ownFeedFor(List.of("PegaIntegration"), INTEGRATION));
        json("survey_feed", args("feed", feed, "build", second));
        recordSourceTypes(second, feed, INTEGRATION);
        final Map<String, Object> integration = group(second, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        assertThat(integration).containsEntry("output_feed", "");
        final String outputFeed = feed + "-INTEGRATION-EVENTS";
        agreed("create_feed", args("build", second, "name", outputFeed, "stream_type", "Events", "group",
                INTEGRATION));
        final String own = uuidOf(agreed("create_pipeline", args("build", second, "group", INTEGRATION,
                "template_uuid", template)));
        final List<Long> ownEvents = events(own, stream);
        assertThat(meta(ownEvents.getFirst()).getFeedName()).isEqualTo(outputFeed);
        client.answeringWith(McpTestClient::agree);
        json("get_field_conventions", args());

        // The group's own output feed: the security group's index refused, one of its own.
        assertThat(refusal("draft_index_mapping", args("backend", "lucene", "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", ownEvents))).contains("is the index of group(s) " + SECURITY);
        final String ownIndex = index + "-integration";
        final Map<String, Object> ownDraft = draft("lucene", ownIndex, ownEvents, null, null);
        assertThat(map(ownDraft, "feed_index")).containsEntry("output_feed", outputFeed);
        assertThat((List<String>) map(ownDraft, "feed_index").get("groups")).containsExactly(INTEGRATION);
        json("save_xslt", args("build", second, "name", ownIndex + "-XSLT", "index_plan", map(ownDraft, "plan")));

        promote(second, Map.of("Pipeline", folder, "XSLT", folder));
        final Map<String, Object> registry = registry(folder, feed);
        assertThat(map(registry, FeedIndexes.INDEXES)).containsOnlyKeys(index, ownIndex);
        assertThat(FeedIndexes.indexOfGroup(registry, SECURITY)).containsEntry("index", index);
        assertThat(FeedIndexes.groupsOf(FeedIndexes.indexOfGroup(registry, SECURITY))).containsExactly(SECURITY);
        assertThat(FeedIndexes.indexOfGroup(registry, INTEGRATION)).containsEntry("index", ownIndex)
                .containsEntry("output_feeds", List.of(outputFeed));
        assertThat(FeedIndexes.groupsOf(FeedIndexes.indexOfGroup(registry, INTEGRATION)))
                .containsExactly(INTEGRATION);
    }

    @Test
    void twoGroupsIntoTheSameFeedHaveAnIndexEachEachHoldingOnlyItsGroupsEvents() {
        final String feed = "E2E-PEGA-IDX-TWO";
        final String folder = "E2E Feeds/PegaIdxTwo";
        final String securityIndex = "e2e-pega-two-pega-security";
        createFeed("pega-idx-two-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-idx-two-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String indexing = templateUuid("indexing", "Indexing");

        // The first build: the security group, its Events indexed into its own index.
        final String first = "pega-idx-two-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaSecurityEvent"), null));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed, SECURITY);
        group(first, feed, SECURITY, "PegaSecurityEvent", TestE2eSourceTypeGroups.securityMapping(), stream);
        final String security = uuidOf(agreed("create_pipeline", args("build", first, "group", SECURITY,
                "template_uuid", template)));
        final List<Long> securityEvents = events(security, stream);
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> securityPlan = map(draft("lucene", securityIndex, securityEvents, null, null),
                "plan");
        final String securityIndexDoc = uuidOf(agreed("create_index_doc", args("build", first, "backend", "lucene",
                "name", securityIndex, "time_field", securityPlan.get("time_field"))));
        final String securityXslt = uuidOf(json("save_xslt", args("build", first, "name", securityIndex + "-XSLT",
                "index_plan", securityPlan)));
        indexed(first, securityIndex, indexing, securityXslt, securityIndexDoc, securityEvents, security);
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder));

        // The second build: the integration group, its Events into the same feed (the default), and the security
        // group's pipeline run as a new version of it (a build processes only its own pipelines, and a promoted one
        // is copied only as a new version), so the build's Events feed holds both groups' Events.
        final String second = "pega-idx-two-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaIntegration"), null));
        json("survey_feed", args("feed", feed, "build", second));
        recordSourceTypes(second, feed, INTEGRATION);
        assertThat(group(second, feed, INTEGRATION, "PegaIntegration", TestE2eSourceTypeGroups.integrationMapping(),
                stream)).doesNotContainKey("output_feed");
        final String integration = uuidOf(agreed("create_pipeline", args("build", second, "group", INTEGRATION,
                "template_uuid", template)));
        final List<Long> integrationEvents = events(integration, stream);
        final Map<String, Object> securityVersion = agreed("copy_pipeline", args("build", second, "source_uuid",
                security, "new_name", feed + " " + SECURITY + " V1.0"));
        assertThat(securityVersion).doesNotContainKey("working_copy");
        assertThat(String.valueOf(securityVersion.get("name"))).isNotEqualTo(feed + " " + SECURITY + " V1.0");
        final String securityCopy = uuidOf(securityVersion);
        final List<Long> securityCopyEvents = events(securityCopy, stream);
        final String eventsFeed = meta(integrationEvents.getFirst()).getFeedName();
        assertThat(meta(securityCopyEvents.getFirst()).getFeedName()).isEqualTo(eventsFeed);
        assertThat(meta(integrationEvents.getFirst()).getPipelineUuid()).isEqualTo(integration);

        // Its own index, nothing asked about sharing, nothing of the security group's inherited.
        client.answeringWith(McpTestClient::agree);
        final int formsBefore = client.forms().size();
        final String integrationIndex = FeedIndexes.groupIndexName(securityIndex, List.of(SECURITY), INTEGRATION);
        assertThat(integrationIndex).isEqualTo("e2e-pega-two-pega-integration");
        final Map<String, Object> integrationDraft = draft("lucene", integrationIndex, integrationEvents, null,
                null);
        assertThat(formsSince(formsBefore)).noneMatch(form -> form.message().contains("clash with index")
                                                          || form.message().contains("share"));
        final Map<String, Object> feedIndex = map(integrationDraft, "feed_index");
        assertThat(feedIndex).containsEntry("output_feed", feed).containsEntry("group", INTEGRATION)
                .containsEntry("groups", List.of(INTEGRATION))
                .doesNotContainKeys("inherited_fields", "shared_with");
        final Map<String, Object> integrationPlan = map(integrationDraft, "plan");
        final String integrationIndexDoc = uuidOf(agreed("create_index_doc", args("build", second, "backend",
                "lucene", "name", integrationIndex, "time_field", integrationPlan.get("time_field"))));
        final String integrationXslt = uuidOf(json("save_xslt", args("build", second, "name",
                integrationIndex + "-XSLT", "index_plan", integrationPlan)));
        final String integrationIndexing = uuidOf(agreed("create_indexing_pipeline", args("build", second, "name",
                integrationIndex + " - Indexing", "template_uuid", indexing, "xslt_uuid", integrationXslt,
                "events_stream_ids", integrationEvents, "index_uuid", integrationIndexDoc)));
        assertThat(stepSample(integrationIndexing, integrationEvents)).containsEntry("verdict", "clean");
        // A feed-wide filter over the Events feed both groups write to: only the integration pipeline's Events.
        agreed("create_processor_filter", args("pipeline_uuid", integrationIndexing, "feed", eventsFeed,
                "stream_type", "Events", "created_after", "2000-01-01T00:00:00.000Z", "source_pipeline_uuid",
                integration));
        processAll();
        flushIndexShards();

        // Each index holds its own group's documents alone.
        final long securityCount = eventCount(securityEvents);
        final long integrationCount = eventCount(integrationEvents);
        assertThat(securityCount).isPositive();
        assertThat(integrationCount).isPositive();
        assertThat(documents(securityIndexDoc)).isEqualTo(securityCount);
        assertThat(documents(integrationIndexDoc)).as("the integration index, from a feed holding %s security "
                                                      + "Events too", eventCount(securityCopyEvents))
                .isEqualTo(integrationCount);

        promote(second, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder));
        final Map<String, Object> registry = registry(folder, feed);
        assertThat(map(registry, FeedIndexes.INDEXES)).containsOnlyKeys(securityIndex, integrationIndex);
        assertThat(FeedIndexes.groupsOf(FeedIndexes.indexNamed(registry, securityIndex))).containsExactly(SECURITY);
        assertThat(FeedIndexes.groupsOf(FeedIndexes.indexNamed(registry, integrationIndex)))
                .containsExactly(INTEGRATION);
        assertThat((List<String>) FeedIndexes.indexNamed(registry, integrationIndex).get("output_feeds"))
                .containsExactly(feed);
        // The security group's rows name its new version, promoted beside the old one.
        assertThat(jsonOf(registry)).contains(securityCopy);
        assertThat(folderOf(security)).isNotEqualTo(folderOf(securityCopy));
    }

    @Test
    void aNewVersionOfAProductionIndexingPipelineIndexesIntoTheBuildsTestIndexNotTheProductionOne() {
        // Seen in an eval (case 61): a later build's working copy of the group's indexing pipeline, processed in the
        // build, indexed the sample's Events into the live production index, each document without the build's new
        // fields, and the documents could not be taken out again. A copy is now always a new version (decided by the
        // user, 2026-11-17), writing to the same Lucene index: still the build's test index while in the build.
        final String feed = "E2E-PEGA-IDX-WC";
        final String folder = "E2E Feeds/PegaIdxWc";
        final String securityIndex = "e2e-pega-wc-pega-security";
        createFeed("pega-idx-wc-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-idx-wc-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final String indexing = templateUuid("indexing", "Indexing");

        // The first build: the security group, its Events indexed into its own index, promoted.
        final String first = "pega-idx-wc-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaSecurityEvent"), null));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed, SECURITY);
        group(first, feed, SECURITY, "PegaSecurityEvent", TestE2eSourceTypeGroups.securityMapping(), stream);
        final String security = uuidOf(agreed("create_pipeline", args("build", first, "group", SECURITY,
                "template_uuid", template)));
        final List<Long> securityEvents = events(security, stream);
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> securityPlan = map(draft("lucene", securityIndex, securityEvents, null, null),
                "plan");
        final String indexDoc = uuidOf(agreed("create_index_doc", args("build", first, "backend", "lucene",
                "name", securityIndex, "time_field", securityPlan.get("time_field"))));
        final String securityXslt = uuidOf(json("save_xslt", args("build", first, "name", securityIndex + "-XSLT",
                "index_plan", securityPlan)));
        final String securityIndexing = indexed(first, securityIndex, indexing, securityXslt, indexDoc,
                securityEvents, security);
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder));
        final long count = eventCount(securityEvents);
        assertThat(count).isPositive();
        assertThat(documents(indexDoc)).isEqualTo(count);

        // A later build: a working copy of the group's indexing pipeline is refused; its new version, its XSLT
        // given a field the production index lacks, is stepped and processed over the sample's Events.
        final String later = "pega-idx-wc-2";
        assertThat(refusal("copy_pipeline", args("build", later, "source_uuid", securityIndexing, "new_name",
                securityIndex + " - Indexing", "working_copy", true))).contains("never a working copy",
                "Nothing was copied");
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", later, "source_uuid",
                securityIndexing, "new_name", securityIndex + " - Indexing"));
        dump("copy_pipeline indexing new version", copied);
        assertThat(copied).doesNotContainKey("working_copy");
        final String copy = uuidOf(copied);
        final String copyXslt = list(copied, "copied_documents").stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .map(doc -> (String) doc.get("uuid"))
                .findFirst()
                .orElseThrow();
        assertThat(copyXslt).isNotEqualTo(securityXslt);
        final Map<String, Object> widened = new LinkedHashMap<>(securityPlan);
        final List<Map<String, Object>> fields = new ArrayList<>(list(securityPlan, "fields"));
        fields.add(Map.of("name", "NewVersionOnly", "type", "keyword", "source", "EventSource/Generator"));
        widened.put("fields", fields);
        json("save_xslt", args("build", later, "uuid", copyXslt, "index_plan", widened));
        assertThat(luceneFields(new DocRef(LuceneIndexDoc.TYPE, indexDoc))).doesNotContain("NewVersionOnly");
        assertThat(stepSample(copy, securityEvents)).containsEntry("verdict", "clean");
        final Map<String, Object> filter = agreed("create_processor_filter", args("pipeline_uuid", copy,
                "stream_ids", securityEvents, "source_pipeline_uuid", security));
        dump("create_processor_filter new version", filter);
        final Map<String, Object> test = list(filter, "test_index").getFirst();
        assertThat(test).containsEntry("name", securityIndex + "-MCP-TEST")
                .containsEntry("made", true);
        final String testIndex = (String) test.get("uuid");
        assertThat(String.valueOf(filter.get("test_index_note"))).contains("never written to by the build");
        processAll();
        flushIndexShards();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", copy, "stream_ids",
                securityEvents, "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("no unknown fields: %s", gate).containsEntry("gate", "pass");

        // The production index is unchanged: the sample's documents are in the build's test index, which mirrors
        // the production index with the build's new field.
        assertThat(documents(indexDoc)).as("the production index's documents").isEqualTo(count);
        assertThat(documents(testIndex)).as("the build's test index's documents").isEqualTo(count);
        assertThat(folderOf(testIndex)).contains(later);
        assertThat(luceneFields(new DocRef(LuceneIndexDoc.TYPE, testIndex))).contains("NewVersionOnly")
                .containsAll(luceneFields(new DocRef(LuceneIndexDoc.TYPE, indexDoc)));
        // verify_index given the production index searches the test index.
        final Map<String, Object> verified = agreed("verify_index", args("build", later, "index_uuid", indexDoc,
                "backend", "lucene", "stream_ids", securityEvents, "expected_documents", count,
                "fields", List.of("StreamId", "EventId", securityPlan.get("time_field")), "pipeline_uuid", copy));
        dump("verify_index new version", verified);
        assertThat(verified).containsEntry("passed", true).containsKey("test_index");
        assertThat(map(verified, "dashboard")).containsEntry("saved", false);

        // Promoted: the review says the production index gains the plan's new field (no registry in this build:
        // any build's indexing, not only an aggregated feed's group), the new version and its XSLT go beside the
        // original, which is left as it was, the test index is left in the workspace, and the production index's
        // documents are still the first build's alone.
        final Map<String, Object> laterDestinations = Map.of("Pipeline", folder, "XSLT", folder, "Index", folder);
        final Map<String, Object> review = json("promote_build", args("build", later, "destinations",
                laterDestinations));
        assertThat(review).containsEntry("status", "needs_review");
        assertThat(String.valueOf(review.get("index_changes"))).contains("index " + securityIndex,
                "adds NewVersionOnly", "forward only");
        promote(later, laterDestinations);
        assertThat(documents(indexDoc)).isEqualTo(count);
        assertThat(folderOf(testIndex)).contains(later);
        assertThat(readXslt(copyXslt).getDescription()).contains("NewVersionOnly");
        assertThat(readXslt(securityXslt).getDescription()).doesNotContain("NewVersionOnly");
        assertThat(folderOf(copy)).isNotEqualTo(folderOf(securityIndexing));
        assertThat(luceneFields(new DocRef(LuceneIndexDoc.TYPE, indexDoc))).contains("NewVersionOnly");

        // Production indexing of a new stream, through the promoted pipelines (the new version indexing): no field
        // unknown to the index, and the new field searchable.
        final long next = storeParts(feed, List.of(Map.entry(Map.of(), text)));
        feedFilter(feed, "Raw Events", security);
        processAll();
        feedFilter(feed, "Events", copy);
        processAll();
        flushIndexShards();
        final List<String> logged = streamsOf(feed).stream()
                .filter(meta -> "Error".equals(meta.getTypeName())
                                && copy.equals(meta.getPipelineUuid()))
                .map(meta -> storedText(meta.getId()))
                .toList();
        assertThat(logged).as("production indexing after stream %s", next)
                .noneMatch(errors -> errors.contains("unknown field"));
        final Map<String, Object> found = json("search_events", args("index", indexDoc, "where",
                List.of(Map.of("field", "NewVersionOnly", "value", "Pega")), "limit", 3, "allStreams", true,
                "timeFrom", "2000-01-01T00:00:00.000Z"));
        dump("search_events NewVersionOnly", found);
        assertThat(number(found.get("total"))).as("documents with the new field").isPositive();
    }

    // --- the steps ---

    // build_translation_xslt group=: the group saved with its mapping, its pipeline as the user chose it.
    private Map<String, Object> group(final String build,
                                      final String feed,
                                      final String name,
                                      final String sourceType,
                                      final Map<String, Object> mapping,
                                      final long stream) {
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "group", name, "source_types", List.of(sourceType), "schema_version", VERSION,
                "stream_ids", List.of(stream), "mapping", mapping));
        assertThat(saved).as("group %s saved: %s", name, saved.get("problems")).containsEntry("ok", true);
        return map(saved, "group");
    }

    // The Events of the sample, processed through a pipeline once it steps clean.
    private List<Long> events(final String pipeline, final long stream) {
        final Map<String, Object> stepped = stepSample(pipeline, List.of(stream));
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final List<Long> events = ids(list(process(pipeline, List.of(stream)), "streams").getFirst(), "events");
        assertThat(events).hasSize(1);
        return events;
    }

    // draft_index_mapping for the Events, with the fields given, sharing the group's index named (or none).
    private Map<String, Object> draft(final String backend,
                                      final String index,
                                      final List<Long> events,
                                      final List<Map<String, Object>> extras,
                                      final String shareWith) {
        final Map<String, Object> arguments = args("backend", backend, "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", events);
        if (extras != null) {
            arguments.put("extra_fields", extras);
        }
        if (shareWith != null) {
            arguments.put("share_index_with", shareWith);
        }
        final Map<String, Object> drafted = agreed("draft_index_mapping", arguments);
        assertThat(drafted).as("drafted: %s", drafted).containsKey("plan");
        return drafted;
    }

    // The group's indexing pipeline, stepped clean and processing its sample Events (from its group's pipeline only).
    // Returns the indexing pipeline's UUID.
    private String indexed(final String build,
                           final String index,
                           final String template,
                           final String xslt,
                           final String indexDoc,
                           final List<Long> events,
                           final String source) {
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", build, "name",
                index + " - Indexing", "template_uuid", template, "xslt_uuid", xslt, "events_stream_ids", events,
                "index_uuid", indexDoc)));
        assertThat(stepSample(pipeline, events)).containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", pipeline, "stream_ids", events,
                "source_pipeline_uuid", source));
        processAll();
        flushIndexShards();
        return pipeline;
    }

    // The documents a Lucene index holds, over its shards still in use.
    private long documents(final String indexUuid) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.builder()
                .indexUuidSet(Selection.selectNone())
                .build();
        criteria.getIndexUuidSet().add(indexUuid);
        final ResultPage<IndexShard> found = asUser(admin, () -> indexShardService.find(criteria));
        return found.getValues().stream()
                .filter(shard -> shard.getStatus() != IndexShardStatus.DELETED)
                .mapToLong(IndexShard::getDocumentCount)
                .sum();
    }

    // The Events the streams hold.
    private long eventCount(final List<Long> streams) {
        return streams.stream()
                .mapToLong(id -> children(parse(streamText(id)).getDocumentElement(), "Event").size())
                .sum();
    }

    // A stream's text, as Stroom stores it.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                return SourceUtil.readString(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // record_source_types: the table, then the scope form, the build's group suggested by this name.
    private void recordSourceTypes(final String build, final String feed, final String group) {
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id"), "group", group));
        assertThat(written).as("scoped: %s", written).containsEntry("group", group);
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    // The feed's registry, beside it.
    private Map<String, Object> registry(final String folder, final String feed) {
        return SourceTypeRegistry.read(documentation(documentsIn("System/" + folder, "Documentation",
                feed + SourceTypeRegistry.SUFFIX).getFirst().getUuid()));
    }

    private List<String> luceneFields(final DocRef index) {
        return asUser(admin, () -> indexFieldService.findFields(new FindFieldCriteria(new PageRequest(0, 500),
                FindFieldCriteria.DEFAULT_SORT_LIST, index)).getValues().stream().map(Field::getFldName).toList());
    }

    // --- plans ---

    private static String plannedName(final Map<String, Object> plan, final String source) {
        return list(plan, "fields").stream()
                .filter(field -> source.equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field planned from " + source + ": " + plan));
    }

    private static String plannedType(final Map<String, Object> plan, final String name) {
        return planned(plan, name).get("type").toString();
    }

    private static String plannedSource(final Map<String, Object> plan, final String name) {
        return planned(plan, name).get("source").toString();
    }

    private static Map<String, Object> planned(final Map<String, Object> plan, final String name) {
        return list(plan, "fields").stream()
                .filter(field -> name.equals(field.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field " + name + ": " + plan));
    }

    // --- the user ---

    // A user who, for each clash, gives this build's field another name where its type differs, and says it means
    // the same where only its meaning might: agreeing to everything else.
    private static Function<ElicitFormRequest, ElicitResult> resolving() {
        return form -> {
            final ElicitResult agreed = McpTestClient.agree(form);
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            if (form.requestedSchema().get("properties") instanceof final Map<?, ?> fields) {
                fields.forEach((key, field) -> {
                    if (field instanceof final Map<?, ?> schema
                        && schema.get("enum") instanceof final List<?> options) {
                        options.stream()
                                .map(String::valueOf)
                                .filter(option -> option.startsWith("Rename:") || option.startsWith("Same meaning"))
                                .findFirst()
                                .ifPresent(option -> content.put(String.valueOf(key), option));
                    }
                });
            }
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }

    // A user who chooses these source types for the build's scope, gives the group named an output feed of its own,
    // and agrees to everything else.
    private static Function<ElicitFormRequest, ElicitResult> ownFeedFor(final List<String> scope,
                                                                        final String ownGroup) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope, null);
        return form -> {
            if (form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)) {
                return scoping.apply(form);
            }
            final ElicitResult agreed = McpTestClient.agree(form);
            if (!form.message().contains("'" + ownGroup + "'") || !form.message().contains("is new")) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            properties.forEach((key, field) -> ((List<?>) ((Map<?, ?>) field).get("enum")).stream()
                    .map(String::valueOf)
                    .filter(option -> option.startsWith("To an output feed of its own"))
                    .findFirst()
                    .ifPresent(option -> content.put(String.valueOf(key), option)));
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }
}
