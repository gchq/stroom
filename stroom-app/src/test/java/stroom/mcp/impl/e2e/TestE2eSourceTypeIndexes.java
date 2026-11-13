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
import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.util.shared.PageRequest;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
/// * the groups whose pipelines write to the feed's own Events share one index across builds: the first build's
///   backend and naming are the feed's shared decisions, kept in its registry and inherited by the next build (a
///   change refused), whose plan for its own group's pipeline's Events is the union of the groups' fields; a field
///   of another type and a field of another meaning are held at promotion, then resolved in one form (a field of
///   another name; one field read from both); promotion adds the build's new fields to the index (and to the Lucene
///   index doc), on Lucene and Elasticsearch alike;
/// * a group with an output feed of its own has an index of its own, the feed's own index refused for its Events.
@SuppressWarnings("unchecked")
class TestE2eSourceTypeIndexes extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String STROOM_FLAT = "Stroom flat convention";

    @Inject
    private IndexFieldService indexFieldService;

    @ParameterizedTest
    @ValueSource(strings = {"lucene", "elasticsearch"})
    void groupsShareTheFeedsIndexAndALaterBuildsClashesAreHeldUntilTheUserChooses(final String backend) {
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
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaSecurityEvent"), "for later builds"));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed);
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
        final Map<String, Object> firstDraft = draft(backend, index, firstEvents, null);
        assertThat(map(firstDraft, "feed_index")).containsEntry("output_feed", feed);
        final Map<String, Object> firstPlan = map(firstDraft, "plan");
        if (lucene) {
            agreed("create_index_doc", args("build", first, "backend", backend, "name", index, "time_field",
                    firstPlan.get("time_field")));
        }
        json("save_xslt", args("build", first, "name", index + "-XSLT", "index_plan", firstPlan));
        promote(first, Map.of("Pipeline", folder, "XSLT", folder, "Index", folder));
        final Map<String, Object> afterFirst = registry(folder, feed);
        final Map<String, Object> firstIndex = FeedIndexes.index(afterFirst, feed);
        assertThat(firstIndex).containsEntry("index", index).containsEntry("backend", backend);
        assertThat((List<String>) firstIndex.get("groups")).containsExactly(SECURITY);
        assertThat(FeedIndexes.recordedFields(firstIndex)).hasSameSizeAs(list(firstPlan, "fields"));
        assertThat(FeedIndexes.indexing(afterFirst)).containsEntry("backend", backend)
                .containsEntry("naming", Map.of("convention", "stroom-flat"));
        if (lucene) {
            assertThat(map(firstIndex, "index_doc")).containsEntry("type", LuceneIndexDoc.TYPE);
        }

        // A later build: the integration group, its Events into the feed's, by a pipeline of its own.
        final String later = "pega-idx-2" + tag;
        json("start_build", args("build", later, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("PegaIntegration"), "for later builds"));
        json("survey_feed", args("feed", feed, "build", later));
        recordSourceTypes(later, feed);
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
        assertThat(formsSince(formsBefore)).isEmpty();
        final String other = lucene
                ? "elasticsearch"
                : "lucene";
        assertThat(refusal("get_field_conventions", args("backend", other))).contains(
                feed + SourceTypeRegistry.SUFFIX, "change_shared");
        assertThat(refusal("draft_index_mapping", args("backend", backend, "index_name", index + "-other",
                "convention", "stroom-flat", "events_stream_ids", laterEvents))).contains("has index '" + index + "'",
                feed + SourceTypeRegistry.SUFFIX);

        // A plan whose fields clash with the index's, saved as an agent might: promotion is held.
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
        final String held = refusal("promote_build", args("build", later, "destinations", Map.of("XSLT", folder,
                "Pipeline", folder)));
        dump("promote_build held", held);
        assertThat(held).contains("Not promoted: held", "Field " + typeField + ": long", "Field " + hostField,
                "draft_index_mapping");
        assertThat(held).contains(lucene
                ? "Documents already indexed keep the old type"
                : "Elasticsearch can't change a field's type");

        // Drafted again: the union of the groups' fields (the security group's read from its XSLT through the
        // registry, its pipeline not this build's), the clashes put to the user in one form.
        final int clashFormsBefore = client.forms().size();
        client.answeringWith(resolving());
        final Map<String, Object> laterDraft = draft(backend, index, laterEvents, List.of(
                Map.of("name", typeField, "type", "long", "source", "EventDetail/TypeId"),
                Map.of("name", hostField, "type", hostType, "source", "EventSource/Device/IPAddress")));
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
        assertThat(feedIndex).containsEntry("output_feed", feed).containsEntry("index", index);
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

        // Saved again from the resolved plan: promotion adds its new fields to the feed's index.
        json("save_xslt", args("build", later, "uuid", laterXslt, "index_plan", laterPlan));
        final Map<String, Object> review = json("promote_build", args("build", later, "destinations",
                Map.of("XSLT", folder, "Pipeline", folder)));
        assertThat(review).containsEntry("status", "needs_review");
        assertThat(String.valueOf(review.get("index_changes"))).contains("index " + index, "adds", renamed,
                "forward only");
        promote(later, Map.of("XSLT", folder, "Pipeline", folder));
        final Map<String, Object> laterIndex = FeedIndexes.index(registry(folder, feed), feed);
        assertThat(laterIndex).containsEntry("index", index);
        assertThat((List<String>) laterIndex.get("groups")).containsExactlyInAnyOrder(SECURITY, INTEGRATION);
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
        final String build = "pega-idx-own";
        createFeed("pega-idx-own-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-idx-own-src", Map.of("Feed", folder));
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(ownFeedFor(List.of("PegaSecurityEvent", "PegaIntegration"), INTEGRATION));
        json("survey_feed", args("feed", feed, "build", build));
        recordSourceTypes(build, feed);
        final Map<String, Object> security = group(build, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        final Map<String, Object> integration = group(build, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        final String outputFeed = feed + "-INTEGRATION-EVENTS";
        agreed("create_feed", args("build", build, "name", outputFeed, "stream_type", "Events", "group",
                INTEGRATION));
        final String template = templateUuid("translation", "Event Data (JSON)");
        assertThat(security).doesNotContainKey("output_feed");
        assertThat(integration).containsEntry("output_feed", "");
        final String main = uuidOf(agreed("create_pipeline", args("build", build, "group", SECURITY,
                "template_uuid", template)));
        final String own = uuidOf(agreed("create_pipeline", args("build", build, "group", INTEGRATION,
                "template_uuid", template)));
        final List<Long> mainEvents = events(main, stream);
        final List<Long> ownEvents = events(own, stream);
        assertThat(meta(ownEvents.getFirst()).getFeedName()).isEqualTo(outputFeed);
        client.answeringWith(choosing(STROOM_FLAT));
        json("get_field_conventions", args());
        client.answeringWith(McpTestClient::agree);

        // The feed's own Events: its index. The group's own output feed: the feed's index refused, one of its own.
        final String index = "e2e-pega-idx-own";
        final Map<String, Object> mainDraft = draft("lucene", index, mainEvents, null);
        assertThat(map(mainDraft, "feed_index")).containsEntry("output_feed", feed);
        assertThat((List<String>) map(mainDraft, "feed_index").get("groups")).containsExactly(SECURITY);
        assertThat(refusal("draft_index_mapping", args("backend", "lucene", "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", ownEvents))).contains("is the index of output feed '" + feed
                                                                          + "'");
        final String ownIndex = index + "-integration";
        final Map<String, Object> ownDraft = draft("lucene", ownIndex, ownEvents, null);
        assertThat(map(ownDraft, "feed_index")).containsEntry("output_feed", outputFeed);
        assertThat((List<String>) map(ownDraft, "feed_index").get("groups")).containsExactly(INTEGRATION);
        json("save_xslt", args("build", build, "name", index + "-XSLT", "index_plan", map(mainDraft, "plan")));
        json("save_xslt", args("build", build, "name", ownIndex + "-XSLT", "index_plan", map(ownDraft, "plan")));

        promote(build, Map.of("Pipeline", folder, "XSLT", folder));
        final Map<String, Object> registry = registry(folder, feed);
        assertThat(map(registry, FeedIndexes.INDEXES)).containsOnlyKeys(feed, outputFeed);
        assertThat(FeedIndexes.index(registry, feed)).containsEntry("index", index);
        assertThat((List<String>) FeedIndexes.index(registry, feed).get("groups")).containsExactly(SECURITY);
        assertThat(FeedIndexes.index(registry, outputFeed)).containsEntry("index", ownIndex);
        assertThat((List<String>) FeedIndexes.index(registry, outputFeed).get("groups"))
                .containsExactly(INTEGRATION);
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

    // draft_index_mapping for the Events, with the fields given.
    private Map<String, Object> draft(final String backend,
                                      final String index,
                                      final List<Long> events,
                                      final List<Map<String, Object>> extras) {
        final Map<String, Object> arguments = args("backend", backend, "index_name", index, "convention",
                "stroom-flat", "events_stream_ids", events);
        if (extras != null) {
            arguments.put("extra_fields", extras);
        }
        final Map<String, Object> drafted = agreed("draft_index_mapping", arguments);
        assertThat(drafted).as("drafted: %s", drafted).containsKey("plan");
        return drafted;
    }

    // record_source_types: the table, then the scope form.
    private void recordSourceTypes(final String build, final String feed) {
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        json("record_source_types", args("build", build, "feed", feed, "reviewed", shown.get("review_id")));
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
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope,
                "for later builds");
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
