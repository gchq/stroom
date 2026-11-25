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

package stroom.mcp.impl.tools;

import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.ToolOutputs;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.processing.ProcessorFilterAccess;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// promote_build: the layout, the documents moved, the versions consolidated, and what `TestE2ePlanWalk` and
/// `TestE2eSourceDocs` (stroom-app) expect of it.
class TestPromoteBuildTool {

    private static final String BUILD = "bb";
    // A Documentation doc's text, whose working copies are still written back (decided by the user, 2026-11-17: a
    // pipeline's code and an aggregated feed's shared modules change only by a new version).
    private static final String NOTES_V1 = "# ACME notes\n\nDoors, v1.\n";
    private static final String NOTES_V2 = "# ACME notes\n\nDoors, v2.\n";
    private static final String NOTES_BY_HAND = "# ACME notes\n\nDoors, v1 by hand.\n";
    // An unchanged XSLT working copy, only discarded.
    private static final String PLAIN_XSLT = "<xsl:stylesheet>plain-v1</xsl:stylesheet>";
    // The last option of where a role goes.
    private static final String OTHER = "another folder (type it)";

    private final DocumentationWorld docs = new DocumentationWorld();
    private final BuildWorld world = docs.world;
    private final PromotionProcessing processing = Mockito.mock(PromotionProcessing.class);
    // The tidy-up's view of Stroom: what reads a test feed, its streams, a test index's shards, what uses either.
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final IndexResource indexResource = Mockito.mock(IndexResource.class);
    private final DocDependencyService dependencies = Mockito.mock(DocDependencyService.class);
    private final PromoteBuildTool tool = new PromoteBuildTool(docs.facts, new PromotionLayout(docs.places),
            docs.places, world.guard(), world.records(), docs.translation.consent, processing,
            Mockito.mock(OutputFeeds.class), docs.writes,
            world.documentAccess, docs.translation.history, docs.pendingDocs,
            new PromotionTidyUp(world.guard(), world.records(), world.documentAccess, () -> processorFilters,
                    () -> metaService, () -> indexResource, () -> dependencies));

    @BeforeEach
    void setUp() {
        // The user may manage processors, as promote_build needs.
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(true);
        // No follow-on passed over for indexing the same Events twice, unless a test says.
        Mockito.when(processing.withoutDoubleIndexing(any(), any(), any())).thenAnswer(inv ->
                new PromotionProcessing.Followed(inv.getArgument(0), List.of()));
    }

    @Test
    void aBuildIsNotPromotedWhileADocItWroteIsPending() {
        // Seen in testing (chat 144): the agent said the documentation was done and went on with nothing written.
        final ToolCallContext context = BuildToolCalls.context();
        docs.pendingDocs.remember(context.session(), "p", "Acme-Events", "## Purpose and data\n\nx\n", Map.of());

        assertThat(docs.refusal(tool, context, DocumentationWorld.args(Map.of("build", BUILD))))
                .contains("pipeline 'Acme-Events'", "not written yet");
    }

    @Test
    void aBuildDescribingAPipelineInPlaceHasNothingToPromote() {
        // Seen in eval cases 106-109 on Haiku and Sonnet: promote_build called on a build that described the
        // pipeline in place, its review laying out the source notes' working copy and the survey.
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(DocumentationDoc.TYPE, "n", "bb source notes", null));
        docs.facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", Map.of("name",
                "ACME-Events", "uuid", "p"));

        final String refused = docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(Map.of(
                "build", BUILD, "destinations", Map.of("Feed", "System/Feeds/Acme"))));

        assertThat(refused).contains("Nothing to promote", "describes 'ACME-Events' in place",
                "no new version");
        // Refused before any review: nothing for the user to approve.
        assertThat(refused).doesNotContain("review_id");
    }

    @Test
    void aBuildStartedFromACopyIsPromotedAsBefore() {
        // The gate is the in-place start's only: a copy (a new version) is promoted.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-V1.1-Events", "Events"));
        docs.facts.startFrom = Map.of("choice", ExistingTranslations.EXISTING, "pipeline", Map.of("name",
                "ACME-V1.0-Events", "uuid", "p"));

        assertThat(review(Map.of("Feed", "System/Feeds/Acme"))).containsKey("layout");
    }

    @Test
    void theReviewSaysWhichTemplateEachPromotedPipelineInheritsWithItsFlags() {
        // Decided with the user (2026-11-25): promotion re-parents nothing; it says what each pipeline inherits,
        // and flags a template unlike its predecessor's as a fact.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "cef", "ACME-V1.1-CEF", "CEF"));
        final PipelineInheritance inheritance = Mockito.mock(PipelineInheritance.class);
        Mockito.when(inheritance.facts(Mockito.eq("cef"), Mockito.any(), Mockito.any())).thenReturn(Optional.of(
                new PipelineInheritance.Facts("ACME-V1.1-CEF", new DocRef(PipelineDoc.TYPE, "pre", "Kafka (pre)"),
                        List.of("'ACME-V1.1-CEF' inherits 'Kafka (pre)'; its predecessor 'ACME-V1.0-CEF' inherits "
                                + "'Kafka'"))));
        tool.setInheritance(() -> inheritance);

        final Map<String, Object> shown = review(Map.of("Feed", "System/Feeds/Acme"));

        assertThat(shown.get("inheritance")).isEqualTo(List.of("'ACME-V1.1-CEF' inherits 'Kafka (pre)'; its "
                                                               + "predecessor 'ACME-V1.0-CEF' inherits 'Kafka'"));
        assertThat(String.valueOf(shown.get("hint"))).contains("Say the inheritance too");
    }

    @Test
    void eachRolesDocumentsGoInTheirRoleThenVersionFolderUnderTheFeed() {
        // Decided by the user (2026-11-17): <root>/<feed>/<Role>/<version>, role folders always, the version from
        // the pipeline's name.
        exists("System/Feeds/Acme/Events", "System/Feeds/Acme/Indexing", "System/Elastic Indices/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events", "Events"),
                doc(XsltDoc.TYPE, "x", "ACME-V1.0-Events", "Events"), doc(PipelineDoc.TYPE, "ix",
                        "ACME-V1.0-Indexing", "Indexing"));
        final Map<String, Object> destinations = Map.of("Feed", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme",
                "XSLT", "System/Feeds/Acme");

        final Map<String, Object> shown = review(destinations);

        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME  (Feed)\n"
                                                  + "├── Events/\n"
                                                  + "│   └── V1.0/  (new)\n"
                                                  + "│       ├── ACME-V1.0-Events  (Pipeline)\n"
                                                  + "│       └── ACME-V1.0-Events  (XSLT)\n"
                                                  + "└── Indexing/\n"
                                                  + "    └── V1.0/  (new)\n"
                                                  + "        └── ACME-V1.0-Indexing  (Pipeline)\n\n"
                                                  + "Where each role goes (destinations keyed by the role change "
                                                  + "it):\n"
                                                  + "- Events: System/Feeds/Acme/Events/V1.0 (proposed: the "
                                                  + "feed's own location)\n"
                                                  + "- Indexing: System/Feeds/Acme/Indexing/V1.0 (proposed: the "
                                                  + "feed's own location)");
        // Without forms, the agent asks in the chat: each role's options, the proposal first.
        assertThat(shown.get("role_locations")).asList().hasSize(2).first().isEqualTo(Map.of("role", "Events",
                "folder", "System/Feeds/Acme/Events/V1.0", "why", "proposed: the feed's own location", "options",
                List.of("System/Feeds/Acme/Events/V1.0")));
        assertThat(String.valueOf(shown.get("hint"))).contains("where each role's documents go")
                .contains("destinations keyed by the role");
        assertThat(tool.definition().toString()).doesNotContain("role_subfolders");

        // A folder given for one document is where it goes, as given.
        final Map<String, Object> given = new LinkedHashMap<>(destinations);
        given.put("ix", "System/Elastic Indices/Acme");
        assertThat(String.valueOf(review(given).get("layout")))
                .contains("Elastic Indices/\n    └── Acme/\n        └── ACME-V1.0-Indexing  (Pipeline)");
    }

    @Test
    void aRolesLocationElsewhereHasNoRoleFolderAndIsCoveredByTheReview() {
        // Decided by the user (2026-11-17): a role whose root isn't the feed's has no role folder,
        // <root>/<feed>/<version>; the layout the approval is bound to says each role's location.
        exists("System/Feeds/Acme", "System/Elastic Indices");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events", "Events"),
                doc(PipelineDoc.TYPE, "ix", "ACME-V1.0-Indexing", "Indexing"),
                doc(ElasticIndexDoc.TYPE, "ei", "ACME-V1.0-Index", "Indexing"));
        final Map<String, Object> destinations = new LinkedHashMap<>(Map.of("Feed", "/Feeds/Acme"));

        final Map<String, Object> feedsOwn = review(destinations);
        destinations.put("indexing", "/Elastic Indices");
        final Map<String, Object> elsewhere = review(destinations);

        assertThat(String.valueOf(elsewhere.get("layout")))
                .contains("Elastic Indices/\n    └── Acme/  (new)\n        └── V1.0/  (new)\n"
                          + "            ├── ACME-V1.0-Index  (ElasticIndex)\n"
                          + "            └── ACME-V1.0-Indexing  (Pipeline)")
                .contains("Feeds/\n│   └── Acme/\n│       ├── ACME  (Feed)\n│       └── Events/  (new)\n")
                .contains("- Indexing: System/Elastic Indices/Acme/V1.0 (as given)")
                .doesNotContain("Acme/Indexing");
        assertThat(elsewhere.get("review_id")).isNotEqualTo(feedsOwn.get("review_id"));
    }

    @Test
    void eachRolesLocationIsAskedInOneFormWithTheProposalFirstAndKeptForTheApproval() {
        // Decided by the user (2026-11-17): per role, (1) where the sibling feeds' pipelines of that role are,
        // pre-selected, (2) the feed's own location where it differs, (3) a folder the user types.
        siblingWithIndexingElsewhere();
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events", "Events"),
                doc(PipelineDoc.TYPE, "ix", "ACME-V1.0-Indexing", "Indexing"));
        final Map<String, Object> destinations = Map.of("Feed", "/Feeds/Acme");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("a1", "", "a2", "/Custom Indices"));

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));

        assertThat(user.forms()).hasSize(1);
        assertThat(String.valueOf(user.schemas().getFirst())).contains(
                "System/Feeds/Acme/Events/V1.0 (the feed's own folder, where the sibling feeds' Events pipelines are)",
                "System/Elastic Indices/Acme/V1.0 (where the sibling feeds' Indexing pipelines are)",
                "System/Feeds/Acme/Indexing/V1.0 (the feed's own folder)", OTHER);
        assertThat(String.valueOf(shown.get("layout")))
                .contains("Custom Indices/  (new)\n    └── Acme/  (new)\n        └── V1.0/  (new)\n"
                          + "            └── ACME-V1.0-Indexing  (Pipeline)")
                .contains("- Events: System/Feeds/Acme/Events/V1.0 (as the user chose)")
                .contains("- Indexing: System/Custom Indices/Acme/V1.0 (as the user chose)");
        // Kept: the approval's call lays out what was reviewed, and asks no more.
        final BuildToolCalls.FormUser later = BuildToolCalls.declining();
        assertThat(docs.refusal(tool, later.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id"))))).startsWith("The user did not agree to: Promote build");
        assertThat(later.forms()).hasSize(1);
        assertThat(String.valueOf(later.forms().getFirst())).contains("Indexing: System/Custom Indices");
    }

    @Test
    void withoutFormsTheSiblingsLocationIsProposedAndTheFeedsOwnOffered() {
        siblingWithIndexingElsewhere();
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ix", "ACME-V1.0-Indexing", "Indexing"));

        final Map<String, Object> shown = review(Map.of("Feed", "/Feeds/Acme"));

        assertThat(shown.get("role_locations")).isEqualTo(List.of(Map.of("role", "Indexing", "folder",
                "System/Elastic Indices/Acme/V1.0", "why", "proposed: where the sibling feeds' Indexing pipelines are",
                "options", List.of("System/Elastic Indices/Acme/V1.0", "System/Feeds/Acme/Indexing/V1.0"))));
        assertThat(String.valueOf(shown.get("layout"))).contains("└── Elastic Indices/\n    └── Acme/  (new)\n"
                                                                 + "        └── V1.0/  (new)\n"
                                                                 + "            └── ACME-V1.0-Indexing  (Pipeline)");
    }

    @Test
    void aFeedInAFolderNamedAfterItKeepsItsRoleFoldersWhenItsOwnFolderIsGivenAsARolesLocation() {
        // Seen in a user test (2026-11-18): the feed in System/Feeds and Translations/ABC/ABC-FEED, its documents
        // went to ABC-FEED/ABC-FEED/V1.0, Events and Indexing together with no role folders. The question asked for
        // "the folder holding the feed's Events folders": the feed's own folder, given back as the role's location.
        final String own = abcFeed();
        final String expected = own + "/\n"
                                + "├── Events/  (new)\n"
                                + "│   └── V1.0/  (new)\n"
                                + "│       └── ABC-FEED-V1.0-EVENTS  (Pipeline)\n"
                                + "└── Indexing/  (new)\n"
                                + "    └── V1.0/  (new)\n"
                                + "        └── ABC-FEED-V1.0-INDEXING  (Pipeline)";

        // Proposed: the feed's own location.
        assertThat(String.valueOf(review(Map.of()).get("layout"))).startsWith(expected)
                .contains("- Events: " + own + "/Events/V1.0 (proposed: the feed's own location)");
        // Answered in the form (a client sending the folder in the choice's field): the same.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("a1", own, "a2", own));
        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(Map.of(),
                null)));
        assertThat(String.valueOf(shown.get("layout"))).startsWith(expected)
                .contains("- Indexing: " + own + "/Indexing/V1.0 (as the user chose)");
        assertThat(String.valueOf(user.forms().getFirst())).doesNotContain("the feed's Events folders");
        // Given in the chat, destinations keyed by the role (a destination given replaces the choice recorded).
        assertThat(String.valueOf(review(Map.of("Events", own, "indexing",
                "/Feeds and Translations/ABC/abc-feed")).get("layout")))
                .startsWith(expected)
                .doesNotContain("ABC-FEED/ABC-FEED")
                .contains("- Events: " + own + "/Events/V1.0 (as given)");
    }

    @Test
    void aFeedInAFolderNamedAfterItWithSiblingsKeepsItsRoleFolders() {
        final String own = abcFeed();
        world.add(FeedDoc.TYPE, "XYZ-FEED", world.folder("System/Feeds and Translations/ABC/XYZ-FEED").uuid, "xyz");
        world.folder("System/Feeds and Translations/ABC/XYZ-FEED/Events/V1.0");
        Mockito.when(docs.pipelineIndex.read()).thenReturn(Map.of("xyz-ev", new PipelineIndex.Entry("xyz-ev",
                "XYZ-FEED-V1.0-EVENTS", "System/Feeds and Translations/ABC/XYZ-FEED/Events/V1.0", "template", null)));
        docs.facts.stages.put("xyz-ev", "translation");

        final Map<String, Object> proposed = review(Map.of());
        final Map<String, Object> given = review(Map.of("Events", own, "Indexing", own));

        assertThat(String.valueOf(proposed.get("layout")))
                .contains("- Events: System/Feeds and Translations/ABC/ABC-FEED/Events/V1.0 (proposed: where the "
                          + "sibling feeds' Events "
                          + "pipelines are)")
                .contains("ABC-FEED/\n├── Events/  (new)\n│   └── V1.0/  (new)\n");
        assertThat(String.valueOf(given.get("layout"))).doesNotContain("ABC-FEED/ABC-FEED")
                .contains("ABC-FEED/\n├── Events/  (new)\n│   └── V1.0/  (new)\n")
                .contains("└── Indexing/  (new)\n    └── V1.0/  (new)\n");
    }

    @Test
    void eachRolesChoiceShowsItsFullFolderAndATypedAnswerThatIsNoFolderIsAskedAgain() {
        // Seen in chat 194: the feed in System/Feeds/Endpoint, the form offered "System/Feeds" (the folder holding
        // the feed's) as a suggestion with a free box; the user typed "feed's location", which was recorded and laid
        // out as System/feed's location/Endpoint/V1.0.
        endpointFeed();
        final SequencedUser user = new SequencedUser(List.of(
                (schema, intro) -> answerAll(schema, OTHER, "feed's location"),
                (schema, intro) -> answerAll(schema, null, null)));

        final Map<String, Object> shown = docs.call(tool, asking(user), DocumentationWorld.args(args(Map.of(),
                null)));

        assertThat(user.schemas()).hasSize(2);
        final Map<String, Object> first = asMap(asMap(user.schemas().getFirst().get("properties")).get("a1"));
        assertThat(first.get("enum")).asList().containsExactly(
                "System/Feeds/Endpoint/Events/V1.0 (the feed's own folder, where the sibling feeds' Events pipelines "
                + "are)", OTHER);
        assertThat(String.valueOf(user.schemas().getFirst()) + user.intros().getFirst())
                .doesNotContain("holding the feed's folder").doesNotContain("root");
        // Refused and asked again, saying why; never recorded nor laid out.
        assertThat(user.intros().get(1)).startsWith("Not a folder: Events: 'feed's location' is no folder path");
        assertThat(String.valueOf(shown.get("layout")))
                .doesNotContain("feed's location")
                .startsWith("System/Feeds/Endpoint/\n"
                            + "├── Events/  (new)\n"
                            + "│   └── V1.0/  (new)\n"
                            + "│       └── Endpoint-Logs-V1.0-Events  (Pipeline)\n"
                            + "└── Indexing/  (new)\n"
                            + "    └── V1.0/  (new)\n"
                            + "        └── Endpoint-Logs-V1.0-Indexing  (Pipeline)")
                .contains("- Events: System/Feeds/Endpoint/Events/V1.0 (as the user chose)")
                .contains("- Indexing: System/Feeds/Endpoint/Indexing/V1.0 (as the user chose)");
        assertThat(world.records().promotionRoots(BUILD)).isEqualTo(Map.of("Events", "System/Feeds", "Indexing",
                "System/Feeds"));
    }

    @Test
    void anAnswerThatIsNeverAFolderIsRefusedWithNothingRecorded() {
        endpointFeed();
        final SequencedUser user = new SequencedUser(List.of(
                (schema, intro) -> answerAll(schema, OTHER, "feed's location")));

        final String refused = docs.refusal(tool, asking(user), DocumentationWorld.args(args(Map.of(), null)));

        assertThat(user.schemas()).hasSize(3);
        assertThat(refused).contains("'feed's location' is no folder path", "nothing was recorded or moved",
                "destinations keyed by the role (Events, Indexing)", "the feed's own folder "
                                                                    + "('System/Feeds/Endpoint') under Feed");
        assertThat(world.records().promotionRoots(BUILD)).isEmpty();
        // A folder path typed, or the name of a folder in System, is taken.
        exists("System/Elastic Indices");
        final SequencedUser typing = new SequencedUser(List.of(
                (schema, intro) -> answerAll(schema, OTHER, "Elastic Indices")));
        assertThat(String.valueOf(docs.call(tool, asking(typing), DocumentationWorld.args(args(Map.of(),
                null))).get("layout"))).contains("- Indexing: System/Elastic Indices/Endpoint/V1.0 (as the user "
                                                 + "chose)");
    }

    @Test
    void theFeedsOwnFolderGivenForTheFeedReplacesEveryRolesRecordedChoice() {
        // Seen in chat 194: the user said "should be System/Feeds/Endpoint"; the agent passed
        // {'Feed': '/Feeds/Endpoint'}, and the recorded choices ("feed's location") still placed every role.
        endpointFeed();
        world.records().rememberPromotionRoot(BUILD, "Events", "System/feed's location");
        world.records().rememberPromotionRoot(BUILD, "Indexing", "System/feed's location");
        assertThat(String.valueOf(review(Map.of()).get("layout"))).contains("feed's location");

        final Map<String, Object> corrected = review(Map.of("Feed", "/Feeds/Endpoint"));

        assertThat(String.valueOf(corrected.get("layout")))
                .doesNotContain("feed's location")
                .contains("Events/  (new)\n│   └── V1.0/  (new)\n│       └── Endpoint-Logs-V1.0-Events  (Pipeline)")
                .contains("- Events: System/Feeds/Endpoint/Events/V1.0 (as given: the feed's own folder)")
                .contains("- Indexing: System/Feeds/Endpoint/Indexing/V1.0 (as given: the feed's own folder)");
        assertThat(asMap(asList(corrected.get("role_locations")).getFirst())).containsEntry("folder",
                "System/Feeds/Endpoint/Events/V1.0");
        // Recorded in place of the earlier choices: a later call without destinations lays out the same.
        assertThat(world.records().promotionRoots(BUILD)).isEqualTo(Map.of("Events", "System/Feeds", "Indexing",
                "System/Feeds"));
        assertThat(String.valueOf(review(Map.of()).get("layout")))
                .contains("- Indexing: System/Feeds/Endpoint/Indexing/V1.0 (as the user chose)");
        // A role's own destination replaces its recorded choice too, the folder the layout showed for it read as
        // the feed's own; and the hint says which keys change roles.
        exists("System/Elastic Indices");
        final Map<String, Object> byRole = review(Map.of("Indexing", "/Elastic Indices/Endpoint/V1.0"));
        assertThat(String.valueOf(byRole.get("layout")))
                .contains("- Indexing: System/Elastic Indices/Endpoint/V1.0 (as given)");
        assertThat(world.records().promotionRoots(BUILD)).containsEntry("Indexing", "System/Elastic Indices");
        assertThat(String.valueOf(review(Map.of("Indexing", "System/Feeds/Endpoint/Indexing/V1.0")).get("layout")))
                .contains("- Indexing: System/Feeds/Endpoint/Indexing/V1.0 (as given)");
        assertThat(String.valueOf(byRole.get("hint"))).contains("destinations keyed by the role (Events, Indexing)",
                "the feed's own folder ('System/Feeds/Endpoint') under Feed");
    }

    @Test
    void aCorrectionSentInsteadOfTheApprovalIsToldTheKeysThatChangeRoles() {
        // Seen in chat 194: the user said where the documents should go instead of approving.
        endpointFeed();
        final Map<String, Object> shown = review(Map.of("Feed", "/Feeds/Endpoint"));
        final SequencedUser user = new SequencedUser(List.of((schema, intro) -> new ElicitResult(
                ElicitResult.Action.CANCEL, null, Map.of(McpElicitations.SENT_INSTEAD,
                "location is wrong. should be: System/Feeds/Endpoint"))));

        final String told = docs.refusal(tool, asking(user), DocumentationWorld.args(args(Map.of("Feed",
                "/Feeds/Endpoint"), shown.get("review_id"))));

        assertThat(told).contains("sent a message instead", "should be: System/Feeds/Endpoint",
                "destinations keyed by the role (Events, Indexing)",
                "the feed's own folder ('System/Feeds/Endpoint') under Feed (or under a role) puts every role it "
                + "covers in System/Feeds/Endpoint/<Role>/<version>");
    }

    @Test
    void onlyWhatTheBuildsPipelinesUseIsPromotedAndTheRestIsListed() {
        // Seen in a user test (2026-11-18): an older Data Splitter the build had replaced was promoted with the rest.
        // Decided by the user (2026-11-18): what nothing uses stays in the build, listed in the approval.
        final Map<String, Object> destinations = buildWithAReplacedSplitter();

        final Map<String, Object> shown = review(destinations);

        assertThat(String.valueOf(shown.get("layout")))
                .contains("ACME-V1.0-Splitter  (TextConverter)", "ACME Helpers  (XSLT)", "ACME Lists  (Dictionary)")
                .doesNotContain("ACME-V1.0-Splitter-OLD  (TextConverter)", "ACME-V1.0-Splitter-OLD  (Documentation)",
                        "ACME Unused  (XSLT)")
                .contains("Not promoted, left in the build (nothing uses them): ACME-V1.0-Splitter-OLD "
                          + "(TextConverter), ACME-V1.0-Splitter-OLD (Documentation), ACME Unused (XSLT)");
        assertThat(String.valueOf(((Map<?, ?>) approvalForm(destinations, shown.get("review_id")).get("details"))
                .get("not promoted (nothing uses them)")))
                .contains("ACME-V1.0-Splitter-OLD (TextConverter)", "ACME Unused (XSLT)");

        // Promoted: they stay in the build, untouched by the tidy-up; the rest moved.
        promote(BuildToolCalls.user(Map.of("value", true)), destinations);
        final String buildFolder = world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD).uuid;
        assertThat(world.nodes.get("old").parent).isEqualTo(buildFolder);
        assertThat(world.nodes.get("old-doc").parent).isEqualTo(buildFolder);
        assertThat(world.nodes.get("unused").parent).isEqualTo(buildFolder);
        assertThat(world.nodes.get("ds").parent).isNotEqualTo(buildFolder);
        assertThat(world.nodes.get("h").parent).isNotEqualTo(buildFolder);
    }

    @Test
    void aDocumentNothingUsesIsPromotedWhenGivenAFolderOfItsOwn() {
        final Map<String, Object> destinations = new LinkedHashMap<>(buildWithAReplacedSplitter());
        destinations.put("old", "/Feeds/Acme/Old");

        assertThat(String.valueOf(review(destinations).get("layout")))
                .contains("Old/  (new)\n")
                .doesNotContain("ACME-V1.0-Splitter-OLD (TextConverter)");
    }

    @Test
    void aPipelineWithNoVersionInItsNameGoesInV10WithAWarning() {
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-Events", "Events"));

        assertThat(String.valueOf(review(Map.of("Feed", "/Feeds/Acme")).get("layout")))
                .contains("Events/  (new)\n    └── V1.0/  (new)\n        └── ACME-Events  (Pipeline)")
                .contains("Pipeline 'ACME-Events' has no version in its name (e.g. -V1.0-): placed in V1.0.");
    }

    @Test
    void aVersionTokenOfTheFeedsOwnNameIsNotThePipelinesVersion() {
        // Found in review (2026-11-18): CISCO-ASA-V9-EVENTS of feed CISCO-ASA-V9 was filed in V9.
        exists("System/Feeds/Cisco");
        listed(doc(FeedDoc.TYPE, "f", "CISCO-ASA-V9", null), doc(PipelineDoc.TYPE, "ev", "CISCO-ASA-V9-EVENTS",
                "Events"));

        assertThat(String.valueOf(review(Map.of("Feed", "/Feeds/Cisco")).get("layout")))
                .contains("Events/  (new)\n    └── V1.0/  (new)\n        └── CISCO-ASA-V9-EVENTS  (Pipeline)")
                .contains("Pipeline 'CISCO-ASA-V9-EVENTS' has no version in its name (e.g. -V1.0-): placed in V1.0.")
                .doesNotContain("V9/");
    }

    @Test
    void aCefPipelineAndAReferenceLoaderGoInTheirRolesFolders() {
        // A CEF flattener (Events in, CEF to Kafka) is classed forwarding, filed under CEF; a loader under Reference.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "cef", "ACME-V1.0-CEF", null),
                doc(XsltDoc.TYPE, "cx", "ACME-V1.0-CEF", "CEF"), doc(PipelineDoc.TYPE, "ref",
                        "ACME-V1.0-Reference", null));
        docs.facts.stages.put("cef", PipelineShapes.classify(Map.of("kafka", "StandardKafkaProducer", "xslt",
                "XSLTFilter", "parser", "XMLParser"), Map.of(), Map.of(), () -> false).stage());
        docs.facts.stages.put("ref", "loader");

        assertThat(String.valueOf(review(Map.of("Feed", "/Feeds/Acme")).get("layout")))
                .contains("├── CEF/  (new)\n│   └── V1.0/  (new)\n│       ├── ACME-V1.0-CEF  (Pipeline)\n"
                          + "│       └── ACME-V1.0-CEF  (XSLT)\n")
                .contains("└── Reference/  (new)\n    └── V1.0/  (new)\n        └── ACME-V1.0-Reference  (Pipeline)");
    }

    @Test
    void aBuildOnAnExistingFeedLeavesItsTestFeedInTheWorkspace() {
        // Seen in VS Code: onboarding from a feed already in Stroom, the agent copied records into a test feed
        // (named like any other), and promotion would have made it production with the pipeline's filter selecting
        // it.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME-COPY", null), doc(PipelineDoc.TYPE, "ev", "ACME-Events", "Events"));
        docs.facts.sources = List.of("ACME");

        assertThat(String.valueOf(review(Map.of("Feed", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme"))
                .get("layout"))).contains("Left in the workspace: ACME-COPY (Feed)");
        // Promoted when the user gives it a folder of its own (a new version of the feed, say).
        assertThat(String.valueOf(review(Map.of("f", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme"))
                .get("layout"))).doesNotContain("Left in the workspace");
    }

    @Test
    void theBuildsTestFeedIsNeverPromotedWhateverTheDestinationsSay() {
        // Seen in chat 177: the build's test feed was in the layout (under Events), and the agent asked the user
        // whether to promote it.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "t", "ACME" + OutputFeeds.TEST_FEED, "Events"), doc(PipelineDoc.TYPE, "ev",
                "ACME-Events", "Events"));

        final String layout = String.valueOf(review(Map.of("t", "System/Feeds/Acme", "Feed", "System/Feeds/Acme",
                "Pipeline", "System/Feeds/Acme")).get("layout"));

        assertThat(layout).doesNotContain("ACME-MCP-TEST  (Feed)")
                .contains("Left in the workspace: ACME-MCP-TEST (Feed)")
                .endsWith("The build's test feed ACME-MCP-TEST stays in the build's workspace: it holds only the "
                          + "sample's Events and is never promoted.");
    }

    @Test
    void aSourceDirectlyInAParentOfSourcesGetsAFolderOfItsOwn() {
        // Seen in chat 177: the feed sat directly in /Feeds, its siblings each in a folder of their own; the
        // registry went to /Feeds/<feed>, the output feed and notes to /Feeds, and the rest to /Feeds/Events and
        // /Feeds/Indexing, role folders every source would share.
        final BuildWorld.Node feeds = world.folder("System/Feeds");
        world.add(FeedDoc.TYPE, "NETSKOPE", world.folder("System/Feeds/Netskope").uuid, "n");
        world.add(FeedDoc.TYPE, "AZURE", feeds.uuid, "az");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "AZURE", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "az", "AZURE")));
        docs.facts.sources = List.of("AZURE");
        listed(doc(FeedDoc.TYPE, "t", "AZURE" + OutputFeeds.TEST_FEED, null),
                doc(PipelineDoc.TYPE, "ev", "AZURE Messaging", "Events"),
                doc(XsltDoc.TYPE, "x", "AZURE Messaging", "Events"),
                doc(PipelineDoc.TYPE, "ix", "AZURE Messaging Indexing", "Indexing"),
                doc(DocumentationDoc.TYPE, "r", "AZURE source types", null),
                doc(DocumentationDoc.TYPE, "sn", "azure source notes", null));
        final String expected = "System/Feeds/AZURE/  (new)\n"
                                + "├── Events/  (new)\n"
                                + "│   └── V1.0/  (new)\n"
                                + "│       ├── AZURE Messaging  (Pipeline)\n"
                                + "│       └── AZURE Messaging  (XSLT)\n"
                                + "├── Indexing/  (new)\n"
                                + "│   └── V1.0/  (new)\n"
                                + "│       └── AZURE Messaging Indexing  (Pipeline)\n"
                                + "├── AZURE source types  (Documentation)\n"
                                + "└── azure source notes  (Documentation)";

        // The parent of the sources given by type, as the agent gave it: a folder of the source's own in it.
        assertThat(String.valueOf(review(Map.of("Pipeline", "/Feeds", "XSLT", "/Feeds")).get("layout")))
                .startsWith(expected);
        // None given: each role's documents laid out under the source's own folder all the same (decided by the user,
        // 2026-11-17: a default layout, shown for the user to change).
        assertThat(String.valueOf(review(Map.of()).get("layout"))).startsWith(expected);
        // A folder given for one document is where it goes, as given.
        assertThat(String.valueOf(review(Map.of("Pipeline", "/Feeds", "XSLT", "/Feeds", "ix",
                "/Feeds/Elsewhere")).get("layout")))
                .contains("Elsewhere/");
    }

    @Test
    void anAggregatedFeedsGroupGetsAFolderOfItsOwnBesideTheFeedsSharedDocuments() {
        // Asked for by the user: <feed>/<group>/<role>, the group's XSLT with its pipeline (seen at the feed's folder
        // beside the common module and helpers), the feed's shared documents beside the feed.
        final String feed = "Azure-Audit-V1.0-EVENTS";
        final BuildWorld.Node feeds = world.folder("System/Feeds");
        world.add(FeedDoc.TYPE, "NETSKOPE", world.folder("System/Feeds/Netskope").uuid, "n");
        world.add(FeedDoc.TYPE, feed, feeds.uuid, "az");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, feed, 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "az", feed)));
        docs.facts.sources = List.of(feed);
        module("gx", feed + " M365 Messaging", "group", "M365 Messaging");
        module("cx", feed + " common", "common", null);
        module("hx", feed + " helpers", "helpers", null);
        world.inBuild(BUILD, FeedDoc.TYPE, feed + OutputFeeds.TEST_FEED, "t");
        final DocRef output = world.inBuild(BUILD, FeedDoc.TYPE, "AZURE-M365-MESSAGING-EVENTS", "of");
        world.records().update(BUILD, output, entry -> entry.put(BuildFeeds.GROUP_OUTPUT, "M365 Messaging"));
        world.inBuild(BUILD, DashboardDoc.TYPE, "Azure M365 Messaging-VERIFY", "dash");
        world.docs.put("dash", DashboardDoc.builder().uuid("dash").name("Azure M365 Messaging-VERIFY").version("v0")
                .dashboardConfig(DashboardConfig.builder().components(List.of(ComponentConfig.builder()
                        .type("query").id("query").name("Query")
                        .settings(QueryComponentSettings.builder()
                                .dataSource(new DocRef(ElasticIndexDoc.TYPE, "ei")).build())
                        .build())).build())
                .build());
        listed(doc(FeedDoc.TYPE, "t", feed + OutputFeeds.TEST_FEED, null),
                doc(FeedDoc.TYPE, "of", "AZURE-M365-MESSAGING-EVENTS", null),
                doc(XsltDoc.TYPE, "gx", feed + " M365 Messaging", "Events"),
                doc(XsltDoc.TYPE, "cx", feed + " common", "Events"),
                doc(XsltDoc.TYPE, "hx", feed + " helpers", "Events"),
                doc(PipelineDoc.TYPE, "ev", "Azure M365 Messaging", "Events"),
                doc(DocumentationDoc.TYPE, "evd", "Azure M365 Messaging", "Events"),
                doc(ElasticIndexDoc.TYPE, "ei", "Azure M365 Messaging Index", "Indexing"),
                doc(DocumentationDoc.TYPE, "eid", "Azure M365 Messaging Index", "Indexing"),
                doc(PipelineDoc.TYPE, "ix", "Azure M365 Messaging Indexing", "Indexing"),
                doc(XsltDoc.TYPE, "ixx", "Azure M365 Messaging Indexing", "Indexing"),
                doc(DocumentationDoc.TYPE, "ixd", "Azure M365 Messaging Indexing", "Indexing"),
                doc(DashboardDoc.TYPE, "dash", "Azure M365 Messaging-VERIFY", "Indexing"),
                doc(DocumentationDoc.TYPE, "r", feed + " source types", null),
                doc(DocumentationDoc.TYPE, "sn", feed + " source notes", null));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds", "XSLT", "/Feeds", "ElasticIndex",
                "/Feeds", "Documentation", "/Feeds", "Dashboard", "/Feeds");

        assertThat(review(destinations).get("layout")).isEqualTo(
                "System/Feeds/Azure-Audit-V1.0-EVENTS/  (new)\n"
                + "├── M365 Messaging/  (new)\n"
                + "│   ├── AZURE-M365-MESSAGING-EVENTS  (Feed)\n"
                + "│   ├── Events/  (new)\n"
                + "│   │   └── V1.0/  (new)\n"
                + "│   │       ├── Azure M365 Messaging  (Documentation)\n"
                + "│   │       ├── Azure M365 Messaging  (Pipeline)\n"
                + "│   │       └── Azure-Audit-V1.0-EVENTS M365 Messaging  (XSLT)\n"
                + "│   └── Indexing/  (new)\n"
                + "│       └── V1.0/  (new)\n"
                + "│           ├── Azure M365 Messaging Index  (Documentation)\n"
                + "│           ├── Azure M365 Messaging Index  (ElasticIndex)\n"
                + "│           ├── Azure M365 Messaging Indexing  (Documentation)\n"
                + "│           ├── Azure M365 Messaging Indexing  (Pipeline)\n"
                + "│           ├── Azure M365 Messaging Indexing  (XSLT)\n"
                + "│           └── Azure M365 Messaging-VERIFY  (Dashboard)\n"
                + "├── Common/  (new)\n"
                + "│   └── V1.0/  (new)\n"
                + "│       └── Azure-Audit-V1.0-EVENTS common  (XSLT)\n"
                + "├── Helpers/  (new)\n"
                + "│   └── V1.0/  (new)\n"
                + "│       └── Azure-Audit-V1.0-EVENTS helpers  (XSLT)\n"
                + "├── Azure-Audit-V1.0-EVENTS source notes  (Documentation)\n"
                + "└── Azure-Audit-V1.0-EVENTS source types  (Documentation)\n\n"
                + "Left in the workspace: Azure-Audit-V1.0-EVENTS-MCP-TEST (Feed)\n\n"
                + "Where each role goes (destinations keyed by the role change it):\n"
                + "- Events: System/Feeds/Azure-Audit-V1.0-EVENTS/M365 Messaging/Events/V1.0 (proposed: the "
                + "feed's own location)\n"
                + "- Indexing: System/Feeds/Azure-Audit-V1.0-EVENTS/M365 Messaging/Indexing/V1.0 (proposed: the "
                + "feed's own location)\n\n"
                + "Pipeline 'Azure M365 Messaging' has no version in its name (e.g. -V1.0-): placed in V1.0.\n"
                + "Pipeline 'Azure M365 Messaging Indexing' has no version in its name (e.g. -V1.0-): placed in "
                + "V1.0.\n\n"
                + "Tidy-up after promotion (the user can untick it in the approval):\n"
                + "- test feed 'Azure-Audit-V1.0-EVENTS-MCP-TEST': its 0 stream(s) (0B) marked deleted, as Stroom's "
                + "Data screen deletes them (data retention purges them later), then the feed deleted");
        // A role's location elsewhere: <root>/<feed>/<group>/<version>, the shared modules still under the feed.
        final Map<String, Object> elsewhere = new LinkedHashMap<>(destinations);
        elsewhere.put("Indexing", "/Elastic Indices");
        assertThat(String.valueOf(review(elsewhere).get("layout")))
                .contains("Elastic Indices/  (new)\n    └── Azure-Audit-V1.0-EVENTS/  (new)\n"
                          + "        └── M365 Messaging/  (new)\n            └── V1.0/  (new)\n")
                .contains("├── Common/  (new)\n");
    }

    @Test
    void aPipelinesDocumentationGoesWhereThePipelineGoesWhenGivenByUuid() {
        // A pipeline given a folder by uuid (a parent of sources): its Documentation goes beside it, not by type into
        // the source's own folder.
        final BuildWorld.Node feeds = world.folder("System/Feeds");
        world.add(FeedDoc.TYPE, "NETSKOPE", world.folder("System/Feeds/Netskope").uuid, "n");
        world.add(FeedDoc.TYPE, "AZURE", feeds.uuid, "az");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "AZURE", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "az", "AZURE")));
        docs.facts.sources = List.of("AZURE");
        listed(doc(PipelineDoc.TYPE, "ev", "AZURE Messaging", "Events"),
                doc(DocumentationDoc.TYPE, "d", "AZURE Messaging", "Events"),
                doc(PipelineDoc.TYPE, "ix", "AZURE Messaging Indexing", "Indexing"),
                doc(DocumentationDoc.TYPE, "di", "AZURE Messaging Indexing", "Indexing"));

        final String layout = String.valueOf(review(Map.of("ev", "/Feeds", "Pipeline", "/Feeds",
                "Documentation", "/Feeds")).get("layout"));

        assertThat(layout).startsWith("System/Feeds/\n"
                                      + "├── AZURE Messaging  (Documentation)\n"
                                      + "├── AZURE Messaging  (Pipeline)\n"
                                      + "└── AZURE/  (new)\n"
                                      + "    └── Indexing/  (new)\n"
                                      + "        └── V1.0/  (new)\n"
                                      + "            ├── AZURE Messaging Indexing  (Documentation)\n"
                                      + "            └── AZURE Messaging Indexing  (Pipeline)\n\n");
    }

    @Test
    void aDashboardOnAnIndexFromElsewhereGoesWithTheBuildsIndexingPipeline() {
        // Seen in production: a new indexing version verified through the existing index doc; its dashboard had no
        // place.
        world.add(PipelineDoc.TYPE, "ACME-V1-Indexing", world.folder("System/Indices/Acme").uuid, "v1");
        exists("System/Indices/Acme/V2");
        final Map<String, Object> indexing = doc(PipelineDoc.TYPE, "ix", "ACME-V2-Indexing", "Indexing");
        indexing.put("version_of", "v1");
        listed(doc(DashboardDoc.TYPE, "dash", "ACME-VERIFY", "Indexing"), indexing);
        final DocRef dashboard = world.inBuild(BUILD, DashboardDoc.TYPE, "ACME-VERIFY", "dash");
        world.docs.put(dashboard.getUuid(), DashboardDoc.builder().uuid("dash").name("ACME-VERIFY").version("v0")
                .dashboardConfig(DashboardConfig.builder().components(List.of(ComponentConfig.builder()
                        .type("query").id("query").name("Query")
                        .settings(QueryComponentSettings.builder()
                                .dataSource(new DocRef(ElasticIndexDoc.TYPE, "prod-index")).build())
                        .build())).build())
                .build());
        docs.facts.stages.put("ix", "indexing");

        // The predecessor alone in its feed's folder: the new version in a role folder there.
        assertThat(review(Map.of()).get("layout")).isEqualTo("System/Indices/Acme/Indexing/V2/  (new)\n"
                                                             + "├── ACME-V2-Indexing  (Pipeline)\n"
                                                             + "└── ACME-VERIFY  (Dashboard)");
    }

    @Test
    void aBuildOfOneRoleStillGetsItsRoleFolder() {
        // Decided by the user (2026-11-17): role folders always, even when one role moves (before, a build of one
        // role got none, so where a version's documents went depended on what else the build held).
        exists("System/Docs");
        listed(doc(DocumentationDoc.TYPE, "d", "ACME-V1.0-Indexing", "Indexing"), doc(PipelineDoc.TYPE, "ix",
                "ACME-V1.0-Indexing", "Indexing"));

        final Map<String, Object> shown = review(Map.of("Documentation", "System/Docs", "Pipeline", "System/Docs"));

        assertThat(String.valueOf(shown.get("layout"))).startsWith("System/Docs/Indexing/V1.0/  (new)\n"
                                                                   + "├── ACME-V1.0-Indexing  (Documentation)\n"
                                                                   + "└── ACME-V1.0-Indexing  (Pipeline)");
    }

    @Test
    void aBuildWithNoDocumentsIsRefused() {
        assertThat(docs.refusal(tool, BuildToolCalls.context(), "{\"build\": \"bb\"}"))
                .isEqualTo("Build 'bb' has no documents");
    }

    @Test
    void aPipelinesDocumentationFollowsThePipelinePromotedWithIt() {
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events",
                null));

        final Map<String, Object> shown = review(Map.of("Pipeline", "System/Feeds/Acme"));

        // Every doc had a destination: on to the folders, to be made.
        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/  (new)\n"
                                                  + "├── ACME-Events  (Documentation)\n"
                                                  + "└── ACME-Events  (Pipeline)");
    }

    @Test
    void theSourcesNotesAndReferenceDocsGoBesideTheFeed() {
        // As TestE2eSourceDocs has it: promoted beside the feed, where later work on it looks.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME-DIR", null), doc(DocumentationDoc.TYPE, "n",
                "Acme directory source notes", null), doc(DocumentationDoc.TYPE, "r",
                "Acme directory reference - audit log reference", null));

        assertThat(review(Map.of("Feed", "System/Feeds/Acme")).get("layout"))
                .isEqualTo("System/Feeds/Acme/\n"
                           + "├── ACME-DIR  (Feed)\n"
                           + "├── Acme directory reference - audit log reference  (Documentation)\n"
                           + "└── Acme directory source notes  (Documentation)");
    }

    @Test
    void theSurveyDocStaysInTheBuildAndTheRegistryGoesBesideTheFeed() {
        // Decided 2026-11-13: the survey doc always stays in the build, unasked; the registry goes beside the feed.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME-DIR", null), doc(DocumentationDoc.TYPE, "s", "ACME-DIR - Survey", null),
                doc(DocumentationDoc.TYPE, "r", "ACME-DIR source types", null));

        final Map<String, Object> shown = review(Map.of("Feed", "System/Feeds/Acme"));

        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME-DIR  (Feed)\n"
                                                  + "└── ACME-DIR source types  (Documentation)\n\n"
                                                  + "Left in the workspace: ACME-DIR - Survey (Documentation)");
        assertThat(shown).doesNotContainKey("registries");
    }

    @Test
    void aDocumentCanBeKeptInTheWorkspaceRatherThanPromoted() {
        // Eval workflow fix_errors (Haiku): a test feed made while fixing had nowhere to go but production, so the
        // agent never promoted the fix.
        exists("System/Feeds/Acme");
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(FeedDoc.TYPE, "f", "ACME-TEST", null));

        final Map<String, Object> shown = call(Map.of("Pipeline", "System/Feeds/Acme", "f", "keep"), null);

        // First the layout, a tree for the chat (asked for by the user: a list in a form was hard to read).
        assertThat(shown).containsEntry("status", "needs_review").containsEntry("layout", "System/Feeds/Acme/\n└── "
                                                                                          + "ACME-Events  "
                                                                                          + "(Pipeline)\n\nLeft in the "
                                                                                          + "workspace: ACME-TEST "
                                                                                          + "(Feed)");
        // The same folder, written as Stroom's own folder tools write it.
        final Map<String, Object> gate = approvalForm(Map.of("Pipeline", "/Feeds/Acme", "f", "keep"),
                shown.get("review_id"));

        assertThat(gate).containsEntry("status", "needs_approval");
        final Map<?, ?> asked = (Map<?, ?>) gate.get("details");
        assertThat(asked.get("folders")).isEqualTo("System/Feeds/Acme");
        // Without the review id, which meant nothing to the user: the approval is bound to it unseen.
        assertThat(String.valueOf(asked.get("layout"))).isEqualTo("1 document(s), as shown in the chat");
    }

    @Test
    void theLayoutCantBeSkippedOnlyReviewedAsShown() {
        // Seen (Delinea SecretServer): reviewed=true on promote_build's first call, so the user approved a form
        // whose layout was never shown in the chat.
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));

        final Map<String, Object> upFront = call(Map.of("Pipeline", "/Feeds/Acme"), true);

        assertThat(upFront).containsEntry("status", "needs_review");
        assertThat(String.valueOf(upFront.get("hint"))).contains("true is not one");
        // Another layout since (the user moved it): reviewed again, not approved on the first one's id.
        final Map<String, Object> moved = call(Map.of("Pipeline", "/Feeds/Elsewhere"), upFront.get("review_id"));
        assertThat(moved).containsEntry("status", "needs_review");
        assertThat(moved.get("review_id")).isNotEqualTo(upFront.get("review_id"));
        assertThat(String.valueOf(moved.get("hint"))).contains("changed since");
        assertThat(approvalForm(Map.of("Pipeline", "/Feeds/Elsewhere"), moved.get("review_id")))
                .containsEntry("status", "needs_approval");
    }

    @Test
    void aLayoutShownInTheChatAndUnchangedIsApprovedWithoutItsReviewId() {
        // Seen in chat 192: the layout was shown, the user said "confirmed", and the agent had to search the chat's
        // history for its review_id before promote_build would take it.
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        final SessionWorkspace workspace = memoryWorkspace();
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = docs.call(tool, inChat(UserQuestions.NONE, workspace),
                DocumentationWorld.args(args(destinations, null)));
        assertThat(shown).containsEntry("status", "needs_review");

        // reviewed left out: the layout shown, unchanged, goes to the short approval form.
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        assertThat(docs.refusal(tool, inChat(user, workspace), DocumentationWorld.args(args(destinations, null))))
                .startsWith("The user did not agree to: Promote build");
        assertThat(user.forms()).hasSize(1);

        // Changed since (another folder): shown again, saying so.
        final Map<String, Object> moved = docs.call(tool, inChat(UserQuestions.NONE, workspace),
                DocumentationWorld.args(args(Map.of("Pipeline", "/Feeds/Elsewhere"), null)));
        assertThat(moved).containsEntry("status", "needs_review");
        assertThat(String.valueOf(moved.get("hint"))).contains("has changed since: show this",
                String.valueOf(shown.get("review_id")));
    }

    @Test
    void everyDocumentWithoutADestinationIsNamedAtOnce() {
        // Seen (Delinea SecretServer): the Dashboard's, then the TextConverter's, a failed call each.
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DashboardDoc.TYPE, "d", "ACME-VERIFY", null),
                doc(TextConverterDoc.TYPE, "t", "ACME wrapper", null));

        final String said = docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(Map.of("build",
                BUILD, "destinations", Map.of("XSLT", "Feeds/Acme"))));

        assertThat(said).contains("Pipeline 'ACME-Events' (p); Dashboard 'ACME-VERIFY' (d); TextConverter 'ACME "
                                  + "wrapper' (t)")
                .contains("by type (Dashboard, Pipeline, TextConverter")
                .contains("'keep'")
                .endsWith("Nothing was changed.");
    }

    @Test
    void documentationTheBuildsOwnChangeLeftBehindHoldsPromotionUpAndAHandEditOnlyWarns() {
        // As TestE2ePlanWalk has it: written before the build's change, the documentation holds promotion up,
        // naming the call; after a hand edit of the XSLT, it is a warning in the approval.
        exists("System/Feeds/Acme");
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        final String stale = "Pipeline 'ACME-Events': the documentation's Field mapping predates the current mapping "
                             + "or XSLT (write_documentation build=<this build> pipeline_uuid='p' "
                             + "stream_ids=<its sample streams> change=<what changed>)";
        docs.facts.checks = List.of(stale + BuildChecks.BLOCKS);

        assertThat(docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(Map.of("build", BUILD,
                "destinations", Map.of("Pipeline", "/Feeds/Acme")))))
                .startsWith("Not promoted: " + stale)
                .contains("once promoted, the pipeline's documentation can only change through a working copy");

        docs.facts.checks = List.of(stale);
        final Map<String, Object> shown = call(Map.of("Pipeline", "/Feeds/Acme"), null);
        assertThat(shown.get("warnings")).isEqualTo(List.of(stale));
        final Map<String, Object> gate = approvalForm(Map.of("Pipeline", "/Feeds/Acme"), shown.get("review_id"));
        // As the form shows it: cut short.
        assertThat(String.valueOf(((Map<?, ?>) gate.get("details")).get("warnings")))
                .startsWith(stale.substring(0, 100));
    }

    @Test
    void aModuleEditedByHandIsAWarningInTheApprovalNeverHoldingPromotionUp() {
        // Decided by the user (2026-11-24): the checks before promotion say a module a pipeline imports that was
        // edited by hand (BuildChecks, from GeneratedDrift.moduleEdits); like any hand edit, it only warns.
        exists("System/Feeds/Acme");
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        final String edited = "Pipeline 'ACME-Events': its imported module 'ACME common' was edited by hand in Stroom "
                              + "since the tools saved it (by john.smith, 2026-11-20): EventSource/Generator is now "
                              + "\"ACME by hand\" (was \"ACME\"). It runs as edited; a later build that adds to it "
                              + "asks the user whether to keep the edit (read into its mapping) or discard it";
        docs.facts.checks = List.of(edited);

        final Map<String, Object> shown = call(Map.of("Pipeline", "/Feeds/Acme"), null);

        assertThat(shown).doesNotContainKey("error");
        assertThat(shown.get("warnings")).isEqualTo(List.of(edited));
    }

    @Test
    void anUnchangedWorkingCopyIsShownAndReportedAsDiscardedNotUpdated() {
        // Seen in an eval: an unchanged working copy XSLT was listed "updated from its working copy" in the layout
        // and updated_in_place while promotion discarded it unchanged.
        exists("System/Feeds/Acme");
        final String prodFolder = world.folder("System/Feeds/Acme").uuid;
        world.add(XsltDoc.TYPE, "ACME-XSLT", prodFolder, "x-prod");
        world.docs.put("x-prod", XsltDoc.builder().uuid("x-prod").name("ACME-XSLT").version("v0")
                .data(PLAIN_XSLT).build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        final DocRef copy = world.inBuild(BUILD, XsltDoc.TYPE, "ACME-XSLT", "x-copy");
        world.docs.put("x-copy", XsltDoc.builder().uuid("x-copy").name("ACME-XSLT").version("v0")
                .data(PLAIN_XSLT).build());
        world.records().update(BUILD, copy, entry -> entry.put("copy_of", "x-prod"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-copy", "ACME-XSLT", "working_copy_of", "x-prod"));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = call(destinations, null);
        assertThat(String.valueOf(shown.get("layout"))).doesNotContain("updated from its working copy");

        final Map<String, Object> promoted = docs.call(tool, BuildToolCalls.answering(Map.of("value", true)),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(promoted).doesNotContainKey("updated_in_place");
        assertThat(promoted.get("promoted")).asList().contains("discarded unchanged working copy XSLT 'ACME-XSLT'");
        assertThat(((XsltDoc) world.docs.get("x-prod")).getData()).isEqualTo(PLAIN_XSLT);
        assertThat(world.nodes).doesNotContainKey("x-copy");
    }

    @Test
    void promotionMovesTheBuildIntoPlaceAndWritesItsWorkingCopiesBackAfterABackup() {
        exists("System/Feeds/Acme");
        final String prodFolder = world.folder("System/Feeds/Acme").uuid;
        world.add(DocumentationDoc.TYPE, "ACME notes", prodFolder, "x-prod");
        world.docs.put("x-prod", DocumentationDoc.builder().uuid("x-prod").name("ACME notes").version("v0")
                .data(NOTES_V1).build());
        final DocRef pipeline = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef documentation = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        final DocRef copy = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME notes", "x-copy");
        world.docs.put("x-copy", DocumentationDoc.builder().uuid("x-copy").name("ACME notes").version("v0")
                .data(NOTES_V2).build());
        world.records().update(BUILD, copy, entry -> entry.put("copy_of", "x-prod"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(DocumentationDoc.TYPE, "x-copy", "ACME notes", "working_copy_of",
                "x-prod"));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = call(destinations, null);
        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME notes  (Documentation: updated from its working copy, "
                                                  + "backed up first)\n"
                                                  + "├── ACME-Events  (Documentation)\n"
                                                  + "└── ACME-Events  (Pipeline)");
        assertThat(approvalForm(destinations, shown.get("review_id"))).containsEntry("status", "needs_approval");
        // Nothing changed until the user approves, which they can't without a form.
        assertThat(docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id"))))).startsWith("Not done: Promote build '" + BUILD + "' as shown in the chat.");
        assertThat(world.guard().buildOf(pipeline)).contains(BUILD);

        final Map<String, Object> promoted = docs.call(tool, BuildToolCalls.answering(Map.of("value", true)),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(promoted).containsEntry("layout", shown.get("layout"));
        assertThat(promoted.get("promoted")).asList().contains(
                "recorded the build's changes as version 1 of documentation 'ACME-Events'",
                "wrote Documentation 'ACME notes' back over 'ACME notes' (backup kept)",
                "moved Pipeline 'ACME-Events' to System/Feeds/Acme",
                "moved Documentation 'ACME-Events' to System/Feeds/Acme",
                "removed the build's workspace folder, now empty");
        // Moved into production: no longer the agent's to change, still known as the tools' own.
        assertThat(world.documentAccess.path(pipeline)).isEqualTo("/Feeds/Acme");
        assertThat(world.tags("p")).containsExactly(WriteGuard.GENERATED);
        assertThat(DocumentationVersions.rowsOf(world.body(documentation.getUuid()))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("version", "1").containsEntry("change", "Created"));
        // The working copy written back over its original, after a backup, then deleted.
        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_V2);
        assertThat(world.nodes).doesNotContainKey("x-copy");
        assertThat(world.nodes.values()).anySatisfy(node -> {
            assertThat(node.name).isEqualTo("ACME notes backup 20261022000000");
            assertThat(node.tags).contains(WriteGuard.GENERATED);
            assertThat(world.documentAccess.path(node.docRef())).isEqualTo("/MCP Workspace/backups");
        });
        assertThat(world.guard().buildFolder(BUILD, false)).isEmpty();
        assertThat(((List<?>) promoted.get("updated_in_place")).stream().map(entry -> String.valueOf(((Map<?, ?>) entry)
                .get("document")))).containsExactly("ACME notes (Documentation)");
        assertThat(((List<?>) promoted.get("folders")).stream()
                .map(entry -> String.valueOf(((Map<?, ?>) entry).get("folder"))))
                .containsExactly("System/Feeds/Acme");
        // Over /mcp, whose sessions are no evidence, no standing instructions are offered.
        assertThat(promoted).doesNotContainKey("standing_instructions");
    }

    @Test
    void aChangedWorkingCopyOfAPromotedPipelinesCodeIsRefusedAtPromotion() {
        // Decided by the user (2026-11-17): a promoted pipeline and the code it runs change only by a new version. A
        // build opened before keeps its working copies: not written back, refused saying what to do.
        exists("System/Feeds/Acme");
        final String prodFolder = world.folder("System/Feeds/Acme").uuid;
        world.add(XsltDoc.TYPE, "ACME-XSLT", prodFolder, "x-prod");
        world.docs.put("x-prod", XsltDoc.builder().uuid("x-prod").name("ACME-XSLT").version("v0")
                .data("<xsl:stylesheet>plain-v1</xsl:stylesheet>").build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef copy = world.inBuild(BUILD, XsltDoc.TYPE, "ACME-XSLT", "x-copy");
        world.docs.put("x-copy", XsltDoc.builder().uuid("x-copy").name("ACME-XSLT").version("v0")
                .data("<xsl:stylesheet>plain-v2</xsl:stylesheet>").build());
        world.records().update(BUILD, copy, entry -> entry.put("copy_of", "x-prod"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-copy", "ACME-XSLT", "working_copy_of", "x-prod"));

        assertThat(docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(args(Map.of("Pipeline",
                "/Feeds/Acme"), null))))
                .contains("XSLT 'ACME-XSLT' is a working copy of a promoted pipeline's code")
                .contains("make a new version")
                .contains("Nothing was changed");
        assertThat(((XsltDoc) world.docs.get("x-prod")).getData())
                .isEqualTo("<xsl:stylesheet>plain-v1</xsl:stylesheet>");
    }

    @Test
    void theApprovalNamesAnOriginalChangedSinceItWasCopiedWhoChangedItWhenAndItsBackup() {
        // Decided by the user (2026-11-18): a production original changed since its working copy was taken is
        // overwritten after its backup, said in the approval the user confirms anyway, never asked on its own.
        final Map<String, Object> destinations = workingCopyOfAChangedOriginal();
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        assertThat(docs.refusal(tool, user.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id"))))).startsWith("The user did not agree to: Promote build");

        final String said = "Documentation 'ACME notes' was changed in production by john.smith on 2026-10-21 "
                            + "09:30 UTC since it was copied: it is overwritten, its earlier version kept as a "
                            + "backup in System/" + BuildWorld.WORKSPACE + "/backups ('ACME notes backup <date and "
                            + "time>')";
        assertThat(shown.get("changed_in_production")).isEqualTo(List.of(said));
        assertThat(String.valueOf(shown.get("hint"))).contains("Say the changed_in_production too");
        // The approval is the only form, and shows it in full.
        assertThat(user.shown()).singleElement().satisfies(form -> assertThat(form)
                .startsWith("Promote build '" + BUILD + "' as shown in the chat")
                .contains("- changed in production since copied:\n  - " + said));
        // Declined: nothing promoted, production as it was.
        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_BY_HAND);
        assertThat(world.nodes).containsKey("x-copy");
    }

    @Test
    void anOriginalChangedSinceItWasCopiedIsOverwrittenAfterItsBackupNothingElseAsked() {
        final Map<String, Object> destinations = workingCopyOfAChangedOriginal();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        assertThat(String.valueOf(shown.get("layout"))).contains("ACME notes  (Documentation: updated from its "
                                                                 + "working copy, backed up first)");
        final Map<String, Object> promoted = docs.call(tool, user.context(), DocumentationWorld.args(args(
                destinations, shown.get("review_id"))));

        assertThat(user.shown()).singleElement().satisfies(form -> assertThat(form).startsWith("Promote build"));
        assertThat(promoted.get("promoted")).asList()
                .contains("wrote Documentation 'ACME notes' back over 'ACME notes' (backup kept)");
        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_V2);
        assertThat(world.nodes).doesNotContainKey("x-copy");
        // The production edit survives in its backup.
        assertThat(world.nodes.values()).anySatisfy(node -> {
            assertThat(node.name).startsWith("ACME notes backup ");
            assertThat(world.documentAccess.path(node.docRef())).isEqualTo("/" + BuildWorld.WORKSPACE + "/backups");
            assertThat(((DocumentationDoc) world.docs.get(node.uuid)).getData()).isEqualTo(NOTES_BY_HAND);
        });
    }

    @Test
    void changedOriginalsIsNoLongerAnArgumentAndKeepIsNotHonoured() {
        // The old answer in the chat: not an argument any more (the call guard drops it as unknown), so it can't
        // keep production from the overwrite the approval says.
        assertThat(tool.definition().toString()).doesNotContain("changed_originals");
        final Map<String, Object> destinations = workingCopyOfAChangedOriginal();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> withAnswer = args(destinations, null);
        withAnswer.put("changed_originals", Map.of("x-copy", "keep"));

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(withAnswer));
        withAnswer.put("reviewed", shown.get("review_id"));
        docs.call(tool, user.context(), DocumentationWorld.args(withAnswer));

        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_V2);
    }

    @Test
    void aBackupThatIsNotMadeStopsTheWriteBack() {
        // Production is only ever overwritten with its earlier version kept.
        final Map<String, Object> destinations = workingCopyOfAChangedOriginal();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        Mockito.when(world.explorerService.copy(any(), any(), Mockito.anyBoolean(), any(),
                any(PermissionInheritance.class))).thenReturn(null);

        assertThat(docs.refusal(tool, user.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id")))))
                .contains("wasn't backed up to System/" + BuildWorld.WORKSPACE + "/backups, so it wasn't "
                          + "overwritten");
        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_BY_HAND);
        assertThat(world.nodes).containsKey("x-copy");
    }

    @Test
    void aWorkingCopyOverAnUnchangedOriginalAsksNothingMore() {
        final Map<String, Object> destinations = workingCopyOfAChangedOriginal();
        // As it was when copied.
        world.docs.put("x-prod", ((DocumentationDoc) world.docs.get("x-prod")).copy().version("v0").build());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        docs.call(tool, user.context(), DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(shown).doesNotContainKey("changed_in_production");
        assertThat(user.shown()).noneMatch(form -> form.contains("was changed in production"));
        assertThat(((DocumentationDoc) world.docs.get("x-prod")).getData()).isEqualTo(NOTES_V2);
    }

    // A build whose working copy of production Documentation 'ACME notes' (copied at version v0) is v2, while
    // production was edited by hand since (v1, by john.smith on 2026-10-21 09:30): the destinations to promote it
    // with.
    private Map<String, Object> workingCopyOfAChangedOriginal() {
        exists("System/Feeds/Acme");
        final String prodFolder = world.folder("System/Feeds/Acme").uuid;
        world.add(DocumentationDoc.TYPE, "ACME notes", prodFolder, "x-prod");
        world.docs.put("x-prod", DocumentationDoc.builder().uuid("x-prod").name("ACME notes").version("v1")
                .updateUser("john.smith").updateTimeMs(Instant.parse("2026-10-21T09:30:00Z").toEpochMilli())
                .data(NOTES_BY_HAND).build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef documentation = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        final DocRef copy = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME notes", "x-copy");
        world.docs.put("x-copy", DocumentationDoc.builder().uuid("x-copy").name("ACME notes").version("v0")
                .data(NOTES_V2).build());
        world.records().update(BUILD, copy, entry -> {
            entry.put("copy_of", "x-prod");
            entry.put(BuildRecords.COPY_OF_VERSION, "v0");
        });
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(DocumentationDoc.TYPE, "x-copy", "ACME notes", "working_copy_of",
                "x-prod"));
        assertThat(documentation).isNotNull();
        return Map.of("Pipeline", "/Feeds/Acme");
    }

    @Test
    void theTidyUpIsListedInTheReviewAndOnByDefaultInTheApproval() {
        // Decided by the user (2026-11-17): the review lists everything the tidy-up will do; the approval refers to
        // it and offers it as a choice, ticked to start.
        final Map<String, Object> destinations = buildWithTestData();
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));

        assertThat(shown.get("tidy_up")).isEqualTo(List.of(
                "test feed 'ACME-MCP-TEST': its 2 stream(s) (3K) marked deleted, as Stroom's Data screen deletes "
                + "them (data retention purges them later), then the feed deleted",
                "test index 'ACME-INDEX-MCP-TEST': its 3 shard(s) deleted, then the index doc deleted",
                "verification dashboard 'ACME-INDEX-MCP-TEST-VERIFY': deleted"));
        assertThat(String.valueOf(shown.get("layout")))
                .contains("Left in the workspace: ACME-MCP-TEST (Feed), ACME-INDEX-MCP-TEST (Index), "
                          + "ACME-INDEX-MCP-TEST-VERIFY (Dashboard), ACME - Survey (Documentation)")
                .contains("Tidy-up after promotion (the user can untick it in the approval):\n- test feed "
                          + "'ACME-MCP-TEST': its 2 stream(s)")
                .doesNotContain("stays in the build's workspace");
        assertThat(String.valueOf(shown.get("hint"))).contains("Say the tidy_up too");

        assertThat(docs.refusal(tool, user.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id"))))).startsWith("The user did not agree to: Promote build");
        assertThat(user.forms().getLast().get("details")).isEqualTo(Map.of("build", BUILD, "layout",
                "2 document(s), as shown in the chat", "folders", "System/Feeds/Acme",
                "tidy-up after promotion", "3 test document(s), as listed in the chat (stream counts and sizes as "
                                           + "of the review)"));
        final Map<String, Object> fields = asMap(user.schemas().getLast().get("properties"));
        assertThat(asMap(fields.get(PromotionTidyUp.TIDY_UP))).containsEntry("type", "boolean")
                .containsEntry("title", PromotionTidyUp.CHOICE_TITLE).containsEntry("default", true);
        // Declined: nothing tidied.
        Mockito.verify(metaService, Mockito.never()).updateStatus(any(FindMetaCriteria.class), any(), any());
        assertThat(world.nodes).containsKeys("t", "ti", "vd");
    }

    @Test
    void approvedTheTestDataIsTidiedStreamsThenShardsThenDocumentsAndNothingElseTouched() {
        final Map<String, Object> destinations = buildWithTestData();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> promoted = promote(user, destinations);

        assertThat(promoted.get("tidied_up")).isEqualTo(List.of(
                Map.of("document", "Feed 'ACME-MCP-TEST'", "streams_marked_deleted", 2, "deleted", true),
                Map.of("document", "Index 'ACME-INDEX-MCP-TEST'", "shards_deleted", 3, "deleted", true),
                Map.of("document", "Dashboard 'ACME-INDEX-MCP-TEST-VERIFY'", "deleted", true)));
        assertThat(String.valueOf(promoted.get("hint"))).contains("Say what the tidy-up did (tidied_up)");
        // Its layout and tidy-up are for the user to see as they are: content to show, which Stroom AI's chat gives
        // its model whole, not in a stub (seen in chat 188: 9,856 characters stubbed, without layout or tidied_up,
        // and the agent read the saved result twice).
        assertThat(promoted).containsEntry("status", "needs_showing");
        assertThat(ToolOutputs.SHOWN_TO_USER_STATUSES).contains(String.valueOf(promoted.get("status")));
        // Streams, then shards, then the documents (a dashboard before the index it searches).
        final InOrder order = Mockito.inOrder(metaService, indexResource, world.explorerService);
        order.verify(metaService).updateStatus(any(FindMetaCriteria.class), Mockito.<Status>isNull(),
                Mockito.eq(Status.DELETED));
        order.verify(indexResource).reset("ti");
        order.verify(world.explorerService).delete(Mockito.argThat(nodes -> "vd".equals(nodes.getFirst()
                .getUuid())));
        order.verify(world.explorerService).delete(Mockito.argThat(nodes -> "ti".equals(nodes.getFirst()
                .getUuid())));
        order.verify(world.explorerService).delete(Mockito.argThat(nodes -> "t".equals(nodes.getFirst()
                .getUuid())));
        // Only the test feed's streams, and only marked deleted, as the Data screen marks them.
        final ArgumentCaptor<FindMetaCriteria> criteria = ArgumentCaptor.forClass(FindMetaCriteria.class);
        Mockito.verify(metaService).updateStatus(criteria.capture(), Mockito.<Status>isNull(),
                Mockito.eq(Status.DELETED));
        assertThat(criteria.getValue().getExpression()).isEqualTo(MetaExpressionUtil.createFeedExpression(
                new DocRef(FeedDoc.TYPE, "t", "ACME-MCP-TEST")));
        Mockito.verify(metaService, Mockito.never()).delete(Mockito.anyLong());
        Mockito.verify(indexResource, Mockito.times(1)).reset(any());
        assertThat(world.nodes).doesNotContainKeys("t", "ti", "vd");
        // The promoted documents, production and the survey doc and the build's record stay.
        assertThat(world.documentAccess.path(new DocRef(PipelineDoc.TYPE, "p"))).isEqualTo("/Feeds/Acme");
        assertThat(world.nodes).containsKeys("p", "d", "prod-feed", "prod-p", "s");
        assertThat(world.guard().folderContents(BUILD, true)).extracting(WriteGuard.BuildDoc::uuid)
                .contains("s").anyMatch(uuid -> !"s".equals(uuid));
        // The record keeps nothing of what was tidied.
        assertThat(world.records().entry(BUILD, "ti")).doesNotContainKey(BuildTestIndexes.TEST_INDEX_FOR);
    }

    @Test
    void untickedInTheApprovalNothingIsTidied() {
        final Map<String, Object> destinations = buildWithTestData();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, PromotionTidyUp.TIDY_UP,
                false));

        final Map<String, Object> promoted = promote(user, destinations);

        assertThat(promoted).doesNotContainKey("tidied_up").containsEntry("tidy_up", "not done, as the user chose: "
                                                                                    + "the build's test data stays "
                                                                                    + "in the workspace");
        Mockito.verify(metaService, Mockito.never()).updateStatus(any(FindMetaCriteria.class), any(), any());
        Mockito.verify(indexResource, Mockito.never()).reset(any());
        assertThat(world.nodes).containsKeys("t", "ti", "vd");
        assertThat(world.documentAccess.path(new DocRef(PipelineDoc.TYPE, "p"))).isEqualTo("/Feeds/Acme");
    }

    @Test
    void theToolArgumentTurnsTheTidyUpOffAndTheReviewChangesWithIt() {
        // Without forms the user can't untick it: tidy_up=false keeps the test data, and is another layout to review.
        final Map<String, Object> destinations = buildWithTestData();
        final Map<String, Object> on = review(destinations);
        final Map<String, Object> args = new LinkedHashMap<>(args(destinations, null));
        args.put(PromotionTidyUp.TIDY_UP, false);
        final Map<String, Object> off = docs.call(tool, BuildToolCalls.context(), DocumentationWorld.args(args));

        assertThat(off).doesNotContainKey("tidy_up");
        assertThat(String.valueOf(off.get("layout"))).doesNotContain("Tidy-up")
                .endsWith("The build's test feed ACME-MCP-TEST stays in the build's workspace: it holds only the "
                          + "sample's Events and is never promoted.");
        assertThat(off.get("review_id")).isNotEqualTo(on.get("review_id"));
        assertThat(tool.definition().toString()).contains(PromotionTidyUp.TIDY_UP);

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        args.put("reviewed", off.get("review_id"));
        final Map<String, Object> promoted = docs.call(tool, user.context(), DocumentationWorld.args(args));

        assertThat(promoted).doesNotContainKeys("tidied_up", "tidy_up");
        assertThat(asMap(user.schemas().getLast().get("properties"))).doesNotContainKey(PromotionTidyUp.TIDY_UP);
        Mockito.verify(metaService, Mockito.never()).updateStatus(any(FindMetaCriteria.class), any(), any());
        assertThat(world.nodes).containsKeys("t", "ti", "vd");
    }

    @Test
    void theApprovalIsBoundToTheTidyUpsItemsNotTheirLiveCounts() {
        // Found in review (2026-11-18): bound to the stream counts, a test feed still being written to gave a new
        // review id on every call, and the promotion was never approved. A test feed with more streams since the
        // review is the same tidy-up: approved as reviewed, the counts said as of the review.
        final Map<String, Object> destinations = buildWithTestData();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        assertThat(shown).containsEntry("status", "needs_review");
        assertThat(String.valueOf(shown.get("tidy_up"))).contains("stream(s)");
        Mockito.when(metaService.findRows(any())).thenReturn(new ResultPage<>(List.of(row(1, "1024"), row(2, "2048"),
                row(3, "10"))));

        final Map<String, Object> promoted = docs.call(tool, user.context(), DocumentationWorld.args(args(
                destinations, shown.get("review_id"))));

        assertThat(promoted.get("status")).isNotEqualTo("needs_review");
        assertThat(promoted).containsKey("tidied_up");
        assertThat(String.valueOf(user.forms().getLast())).contains("as of the review");
    }

    @Test
    void anotherTidyUpItemSinceTheReviewIsReviewedAgain() {
        final Map<String, Object> destinations = buildWithTestData();
        final Map<String, Object> shown = review(destinations);
        world.inBuild(BUILD, FeedDoc.TYPE, "ACME2" + OutputFeeds.TEST_FEED, "t2");
        listed(doc(FeedDoc.TYPE, "t2", "ACME2" + OutputFeeds.TEST_FEED, null));

        final Map<String, Object> again = call(destinations, shown.get("review_id"));

        assertThat(again).containsEntry("status", "needs_review");
        assertThat(again.get("review_id")).isNotEqualTo(shown.get("review_id"));
        assertThat(String.valueOf(again.get("layout"))).contains("ACME2" + OutputFeeds.TEST_FEED);
    }

    @Test
    void aFailedItemIsSaidAndKeptWhileTheOthersAreTidiedAndThePromotionStands() {
        final Map<String, Object> destinations = buildWithTestData();
        Mockito.when(metaService.updateStatus(any(FindMetaCriteria.class), Mockito.<Status>isNull(),
                        Mockito.eq(Status.DELETED)))
                .thenThrow(new RuntimeException("You do not have permission to delete data"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> promoted = promote(user, destinations);

        assertThat(promoted.get("tidied_up")).asList().first().isEqualTo(Map.of("document", "Feed 'ACME-MCP-TEST'",
                "deleted", false, "failed", "marking its streams deleted: You do not have permission to delete data "
                                            + "(its later steps were skipped; it is left in the workspace)"));
        assertThat(world.nodes).containsKey("t").doesNotContainKeys("ti", "vd");
        assertThat(world.documentAccess.path(new DocRef(PipelineDoc.TYPE, "p"))).isEqualTo("/Feeds/Acme");
    }

    @Test
    void aTestFeedAProductionPipelineReadsOrOneOutsideTheBuildIsNeverTidied() {
        // A feed a pipeline outside the build reads, or one not in the build's workspace, is never a test feed.
        final Map<String, Object> destinations = buildWithTestData();
        Mockito.when(processorFilters.pipelinesReading(eq("ACME-MCP-TEST"), any())).thenReturn(List.of(Map.of(
                "uuid", "prod-p", "name", "ACME-Prod", "reads_events", true)));
        world.add(FeedDoc.TYPE, "OTHER-MCP-TEST", world.folder("System/Feeds/Acme").uuid, "o");
        listed(doc(FeedDoc.TYPE, "o", "OTHER-MCP-TEST", null));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));

        assertThat(String.valueOf(shown.get("layout"))).contains("Not tidied up, left in the workspace:\n"
                + "- test feed 'ACME-MCP-TEST': used outside the build, by pipeline 'ACME-Prod' (its processor "
                + "filter reads it)\n"
                + "- test feed 'OTHER-MCP-TEST': it isn't in the build's workspace")
                .endsWith("The build's test feed ACME-MCP-TEST, OTHER-MCP-TEST stays in the build's workspace: it "
                          + "holds only the sample's Events and is never promoted.");
        assertThat(shown.get("tidy_up")).asList().hasSize(2).noneMatch(line -> String.valueOf(line)
                .contains("test feed"));

        final Map<String, Object> promoted = docs.call(tool, user.context(), DocumentationWorld.args(args(
                destinations, shown.get("review_id"))));

        Mockito.verify(metaService, Mockito.never()).updateStatus(any(FindMetaCriteria.class), any(), any());
        assertThat(promoted.get("tidied_up")).asList().hasSize(2);
        assertThat(world.nodes).containsKeys("t", "o", "prod-feed", "prod-p").doesNotContainKeys("ti", "vd");
    }

    @Test
    void aWorkingCopyOfAFeedsCommonModuleIsRefusedAtPromotion() {
        // Decided by the user (2026-11-17): an aggregated feed's shared modules change only by a new version too. A
        // build opened before keeps its working copy of one: not written back, refused saying what to do.
        exists("System/Feeds/Acme");
        world.add(XsltDoc.TYPE, "ACME common", world.folder("System/Feeds/Acme").uuid, "x-prod");
        world.docs.put("x-prod", XsltDoc.builder().uuid("x-prod").name("ACME common").version("v0")
                .data(commonModule("v1")).build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef copy = world.inBuild(BUILD, XsltDoc.TYPE, "ACME common", "x-copy");
        world.docs.put("x-copy", XsltDoc.builder().uuid("x-copy").name("ACME common").version("v0")
                .data(commonModule("v2")).build());
        world.records().update(BUILD, copy, entry -> entry.put("copy_of", "x-prod"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-copy", "ACME common", "working_copy_of", "x-prod"));

        assertThat(docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(args(Map.of("Pipeline",
                "/Feeds/Acme"), null))))
                .contains("XSLT 'ACME common' is a working copy")
                .contains("build_translation_xslt of the build's group makes the new version of it")
                .contains("Nothing was changed");
        assertThat(((XsltDoc) world.docs.get("x-prod")).getData()).isEqualTo(commonModule("v1"));
    }

    @Test
    void aNewVersionOfAFeedsCommonModuleGoesBesideItsPredecessorAndNothingPromotedIsWritten() {
        // Decided by the user (2026-11-17): a promoted module changes only by a new version, placed beside its
        // predecessor in a version folder as a pipeline's new version is. The build's other documents don't join
        // it there: they aren't the module's.
        exists("System/Feeds/Acme");
        world.add(XsltDoc.TYPE, "ACME common", world.folder("System/Feeds/Acme").uuid, "x-common");
        world.docs.put("x-common", XsltDoc.builder().uuid("x-common").name("ACME common").version("v0")
                .data(commonModule("v1")).build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef version = world.inBuild(BUILD, XsltDoc.TYPE, "ACME common V1.1", "x-v11");
        world.docs.put("x-v11", XsltDoc.builder().uuid("x-v11").name("ACME common V1.1").version("v0")
                .data(commonModule("v2")).build());
        world.records().update(BUILD, version, entry -> entry.put("version_of", "x-common"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-v11", "ACME common V1.1", "version_of",
                "x-common"));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = call(destinations, null);
        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME-Events  (Pipeline)\n"
                                                  + "└── Common/  (new)\n"
                                                  + "    └── V1.1/  (new)\n"
                                                  + "        └── ACME common V1.1  (XSLT)");
        final Map<String, Object> promoted = docs.call(tool, BuildToolCalls.answering(Map.of("value", true)),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(promoted.get("promoted")).asList().contains(
                "moved XSLT 'ACME common V1.1' to System/Feeds/Acme/Common/V1.1",
                "moved Pipeline 'ACME-Events' to System/Feeds/Acme");
        assertThat(promoted).doesNotContainKey("updated_in_place");
        assertThat(((XsltDoc) world.docs.get("x-common")).getData()).isEqualTo(commonModule("v1"));
        assertThat(world.documentAccess.path(version)).isEqualTo("/Feeds/Acme/Common/V1.1");
    }

    @Test
    void aGroupsNewXsltGoesInItsPipelinesVersionFolderWhateverItsOwnName() {
        // Seen in e2e: the group's new XSLT, named for its pipeline's next version before the user settled the
        // pipeline's name (PEGA security-V2, the pipeline given 'v2'), went in a V2 folder of its own, apart from its
        // pipeline in v2.
        exists("System/Feeds/Acme/Messaging");
        final String folder = world.folder("System/Feeds/Acme/Messaging").uuid;
        world.add(PipelineDoc.TYPE, "Acme Messaging", folder, "p-prod");
        world.add(XsltDoc.TYPE, "Messaging", folder, "x-prod");
        final DocRef pipeline = world.inBuild(BUILD, PipelineDoc.TYPE, "Acme Messaging v2", "p-v2");
        world.records().update(BUILD, pipeline, entry -> entry.put("version_of", "p-prod"));
        module("x-v2", "Messaging-V2", "group", "Messaging");
        world.records().update(BUILD, new DocRef(XsltDoc.TYPE, "x-v2", "Messaging-V2"),
                entry -> entry.put("version_of", "x-prod"));
        final Map<String, Object> listedPipeline = FakeBuildFacts.doc(PipelineDoc.TYPE, "p-v2", "Acme Messaging v2",
                "version_of", "p-prod");
        listedPipeline.put("role", "Events");
        docs.facts.docs.add(listedPipeline);
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-v2", "Messaging-V2", "version_of", "x-prod"));

        assertThat(call(Map.of("XSLT", "/Feeds/Acme/Messaging"), null).get("layout")).isEqualTo(
                "System/Feeds/Acme/Messaging/Events/v2/  (new)\n"
                + "├── Acme Messaging v2  (Pipeline)\n"
                + "└── Messaging-V2  (XSLT)");
    }

    @Test
    void aNewEventsVersionGoesBesideItsPredecessorAndItsFollowOnsGainFiltersTheAdminRetiresTheOldOnes() {
        // Decided by the user (2026-11-17): the new version in a sibling version folder; each promoted pipeline
        // reading the predecessor's Events gains a new filter, disabled, reading the new version's (listed in the
        // approval); no buttons disabling the earlier filters. And (2026-11-25): the predecessor's filter selects
        // the build's feed, so the new filter's switch-over is the user's, in order; the predecessor keeps no feed,
        // so the follow-on reading its Events may be retired by the admin once the new version's output is right.
        final Map<String, Object> promoted = newVersionPromoted(ProcessorFilterWrites.allOf(List.of(
                ProcessorFilterWrites.term("Feed", "ACME"), ProcessorFilterWrites.term("Type", "Raw Events"))));

        assertThat(promoted.get("follow_on_filters")).asList().hasSize(1);
        assertThat(promoted.get("next_steps")).asList().hasSize(3);
        assertThat(String.valueOf(asList(promoted.get("next_steps")).getFirst())).startsWith(
                "1. The new filter 40 ('ACME-V1.1-Events') has been drafted and is disabled: old filters already "
                + "select its feeds, so the switch-over is the user's (the tools change no old filter or "
                + "dictionary; in-flight tasks make it unsafe), in this order: (a) Disable filter 3 of pipeline "
                + "[ACME-V1.0-Events](?action=open-doc&docType=Pipeline&docUuid=ev1) (its Processors tab): it "
                + "selects only ACME, which the new filter takes over (b) Let filter 3's in-flight tasks finish (0 "
                + "open now for ACME;").contains("(c) Enable the new filter 40 ('ACME-V1.1-Events')");
        assertThat(asList(promoted.get("next_steps")).subList(1, 3)).isEqualTo(List.of(
                "2. Enable the other new filters when ready: filter 41 ('ACME-CEF', reading the Events of "
                + "'ACME-V1.1-Events').",
                "3. Once the new version's output is right, the admin may disable these (the tools never do): they "
                + "read the Events of an earlier version that translates no feed once switched over: 'ACME-CEF': "
                + "filter 5 (reading the Events of 'ACME-V1.0-Events')."));
        assertThat(String.valueOf(promoted.get("user_actions"))).doesNotContain("disable_processor_filter")
                .contains("switch-3");
    }

    @Test
    void theReviewSaysEachNewFiltersFeedsStreamsAndVolumeWithoutBindingTheReviewToThem() {
        // Decided by the user (2026-11-25): a new filter processes every matching stream it hasn't, a dormant feed's
        // history too, so each feed's streams and volume are said before a filter can run. A live feed's count
        // changes between calls: the review shown is still the one confirmed.
        exists("System/Feeds/Acme");
        final DocRef pipeline = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        Mockito.when(processing.promotionProcessing(any(), any(), any(), any())).thenReturn(List.of(
                new PromotionProcessing.Entry(pipeline, List.of("ACME-A", "ACME-B"), "Raw Events", List.of())));
        final long[] count = {120};
        Mockito.when(metaService.getSelectionSummary(any())).thenAnswer(inv -> new SelectionSummary(count[0], 1,
                Set.of(), 1, Set.of(), 0, 0, 1, Set.of(), new Range<>(0L, 86_400_000L)));
        Mockito.when(metaService.findRows(any())).thenReturn(new ResultPage<>(List.of(row(1, "2048"))));
        tool.setSwitchOver(null, () -> new FeedVolumes(() -> metaService));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = review(destinations);
        count[0] = 121;
        final Map<String, Object> later = review(destinations);

        assertThat(shown.get(PromotionReview.FEED_STREAMS)).asList().containsExactly(
                "ACME-A (Raw Events): 120 streams, about 240K, created 1970-01-01T00:00:00.000Z to "
                + "1970-01-02T00:00:00.000Z",
                "ACME-B (Raw Events): 120 streams, about 240K, created 1970-01-01T00:00:00.000Z to "
                + "1970-01-02T00:00:00.000Z");
        assertThat(String.valueOf(shown.get("hint"))).contains("Say the feed_streams too", "a dormant feed's "
                                                                                           + "history too");
        assertThat(String.valueOf(later.get(PromotionReview.FEED_STREAMS))).contains("121 streams");
        assertThat(later.get("review_id")).isEqualTo(shown.get("review_id"));
        assertThat(PromotionReview.shownDigest(later)).isEqualTo(PromotionReview.shownDigest(shown));
    }

    @Test
    void aBuildOfFeedsInOneFolderPutsItsSharedDocumentsThereAsForOneFeed() {
        // Decided by the user (2026-11-25): where all the source feeds are in one folder, it is used as for one.
        twoFeeds("System/Feeds/Acme", "System/Feeds/Acme");

        final Map<String, Object> shown = review(Map.of());

        assertThat(asMap(asList(shown.get("role_locations")).getFirst()).get("folder"))
                .isEqualTo("System/Feeds/Acme/Events/V1.0");
    }

    @Test
    void aBuildOfFeedsInDifferentFoldersAsksWhereItsSharedDocumentsGo() {
        // Decided by the user (2026-11-25): before, the first feed's folder was taken without a word.
        twoFeeds("System/Feeds/Acme-A", "System/Feeds/Acme-B");

        // Without forms: the question, for the agent to ask.
        assertThat(docs.refusal(tool, BuildToolCalls.context(), DocumentationWorld.args(args(Map.of(), null))))
                .contains("feeds in different folders (ACME-A in System/Feeds/Acme-A; ACME-B in "
                          + "System/Feeds/Acme-B)", "the first feed's folder, or another folder you name",
                        "destinations {'Feed': '<the folder>'} (System/Feeds/Acme-A for the first feed's)",
                        "Nothing was changed");
        // The user's answer, given as the Feed destination.
        assertThat(asMap(asList(review(Map.of("Feed", "/Feeds/Shared")).get("role_locations")).getFirst())
                .get("folder")).isEqualTo("System/Feeds/Shared/Events/V1.0");
        // Asked in a form: the first feed's folder suggested; the answer kept for the calls that follow.
        final SessionWorkspace workspace = memoryWorkspace();
        final List<Map<String, Object>> asked = new ArrayList<>();
        final UserQuestions questions = new SequencedUser(List.of((schema, intro) -> {
            asked.add(schema);
            return answerAll(schema, null, "System/Feeds/Acme-B");
        }));
        final Map<String, Object> shown = docs.call(tool, inChat(questions, workspace),
                DocumentationWorld.args(args(Map.of(), null)));
        assertThat(String.valueOf(asked.getFirst())).contains("Folder for the shared documents",
                "System/Feeds/Acme-A");
        assertThat(asMap(asList(shown.get("role_locations")).getFirst()).get("folder"))
                .isEqualTo("System/Feeds/Acme-B/Events/V1.0");
        // Called again in the chat (no forms now): the folder isn't asked again, and the layout shown is taken.
        assertThat(docs.refusal(tool, inChat(BuildToolCalls.context().userQuestions(), workspace),
                DocumentationWorld.args(args(Map.of(), null))))
                .contains("Promote build 'bb' as shown in the chat").doesNotContain("different folders");
    }

    // A build of two existing source feeds in the folders given, and a new events pipeline.
    private void twoFeeds(final String folderA, final String folderB) {
        world.add(FeedDoc.TYPE, "ACME-A", world.folder(folderA).uuid, "fa");
        world.add(FeedDoc.TYPE, "ACME-B", world.folder(folderB).uuid, "fb");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "ACME-A", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "fa", "ACME-A")));
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "ACME-B", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "fb", "ACME-B")));
        docs.facts.sources = List.of("ACME-A", "ACME-B");
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.0-Events", "ev");
        listed(doc(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events", "Events"));
    }

    @Test
    void anEarlierVersionStillTranslatingOtherFeedsAndWhatReadsItsEventsAreNeverAdvisedToBeRetired() {
        // Seen in the audit (2026-11-25, PromoteBuildTool 780-811): the next steps advised disabling the earlier
        // version's filters while they still translated feeds outside the build, and the follow-on filters reading
        // its Events likewise.
        final Map<String, Object> promoted = newVersionPromoted(ProcessorFilterWrites.allOf(List.of(
                ProcessorFilterWrites.term("Feed", "ACME,ACME-B"), ProcessorFilterWrites.term("Type",
                        "Raw Events"))));

        final List<Object> steps = asList(promoted.get("next_steps"));
        assertThat(String.valueOf(steps.getFirst())).contains("(a) Remove ACME from filter 3 of pipeline",
                "it goes on selecting ACME-B");
        assertThat(steps).contains("3. 'ACME-V1.0-Events' stays for the feeds this build doesn't move: filter 3 "
                                   + "(ACME-B). Never disable or remove it while it serves them.",
                "4. These stay too, as the earlier version they read still translates feeds: 'ACME-CEF': filter 5 "
                + "(reading the Events of 'ACME-V1.0-Events').");
        assertThat(String.valueOf(steps)).doesNotContain("may disable", "disables or removes");
    }

    // A new events version (ev2, of ev1) of the build, its predecessor's filter 3 selecting as given, a CEF flattener
    // reading the predecessor's Events (filter 5), promoted: what promote_build returned.
    private Map<String, Object> newVersionPromoted(final ExpressionOperator predecessorSelects) {
        final DocRef before = world.add(PipelineDoc.TYPE, "ACME-V1.0-Events",
                world.folder("System/Feeds/Acme/Events/V1.0").uuid, "ev1").docRef();
        final DocRef version = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.1-Events", "ev2");
        world.records().update(BUILD, version, entry -> entry.put("version_of", "ev1"));
        final Map<String, Object> listed = FakeBuildFacts.doc(PipelineDoc.TYPE, "ev2", "ACME-V1.1-Events", "version_of",
                "ev1");
        listed.put("role", "Events");
        docs.facts.docs.add(listed);
        final DocRef cef = new DocRef(PipelineDoc.TYPE, "cef", "ACME-CEF");
        final ProcessorFilter reading = ProcessorFilter.builder().id(5).pipelineUuid("cef").pipelineName("ACME-CEF")
                .enabled(true).queryData(QueryData.builder().expression(ProcessorFilterWrites.allOf(List.of(
                        ProcessorFilterWrites.term("Type", "Events"), ProcessorFilterWrites.pipelineTerm(before))))
                        .build()).build();
        final ProcessorFilter old = ProcessorFilter.builder().id(3).pipelineUuid("ev1")
                .pipelineName("ACME-V1.0-Events").enabled(true)
                .queryData(QueryData.builder().expression(predecessorSelects).build()).build();
        Mockito.when(processorFilters.all()).thenReturn(List.of(old, reading));
        final PromotionProcessing.FollowOn followOn = new PromotionProcessing.FollowOn(cef, reading,
                new DocRef(PipelineDoc.TYPE, "ev1", "ACME-V1.0-Events"), version);
        Mockito.when(processing.followOns(any(), any())).thenReturn(List.of(followOn));
        Mockito.when(processing.promotionProcessing(any(), any(), any(), any())).thenReturn(List.of(
                new PromotionProcessing.Entry(version, List.of("ACME"), "Raw Events", List.of())));
        Mockito.when(processing.createPromotionFilters(any(), any(), any(), any())).thenAnswer(inv -> {
            final Map<String, Object> made = new LinkedHashMap<>(Map.of("filter_id", 40, "pipeline",
                    "ACME-V1.1-Events", "feed", "ACME", "enabled", false, "pipeline_link",
                    StroomLinks.CHAT.documentTarget(version)));
            made.put(UserActionEntries.USER_ACTIONS, new ArrayList<>(List.of(UserActionEntries.enableFilter(
                    StroomLinks.CHAT, 40, version, "processes feed ACME"))));
            return new ArrayList<>(List.of(made));
        });
        Mockito.when(processing.createFollowOnFilters(any(), any(), any())).thenReturn(List.of(new LinkedHashMap<>(
                Map.of("filter_id", 41, "pipeline", "ACME-CEF", "reads", "ACME-V1.1-Events", "in_place_of",
                        "ACME-V1.0-Events", "copied_from", 5, "enabled", false))));
        tool.setSwitchOver(() -> new PromotionSwitchOver(() -> processorFilters, new PipelineFeeds(
                Mockito.mock(ProcessorFilterAccess.class), world.documentAccess, null), () -> dependencies,
                Mockito.mock(StreamProcessing.class)), null);
        final Map<String, Object> destinations = Map.of();

        final Map<String, Object> shown = call(destinations, null);
        assertThat(String.valueOf(shown.get("layout"))).startsWith("System/Feeds/Acme/Events/V1.1/  (new)\n"
                                                                   + "└── ACME-V1.1-Events  (Pipeline)");
        assertThat(String.valueOf(shown.get("follow_on_filters"))).contains("'ACME-CEF': a new filter, disabled");
        assertThat(String.valueOf(shown.get("switch_over"))).contains("filter 3 of pipeline 'ACME-V1.0-Events' "
                                                                      + "selects ACME, which moves");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, "choice",
                "From now on (recommended): streams arriving after promotion"));
        final Map<String, Object> promoted = docs.call(tool, user.context(),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));
        assertThat(user.shown()).anySatisfy(form -> assertThat(form).contains("switch-over (yours, after "
                                                                              + "promotion)"));
        assertThat(world.documentAccess.path(version)).isEqualTo("/Feeds/Acme/Events/V1.1");
        assertThat(world.documentAccess.path(before)).isEqualTo("/Feeds/Acme/Events/V1.0");
        return promoted;
    }

    @Test
    void aNewVersionsFilterReadsWhatTheUserChoosesAtPromotion() {
        // Decided by the user (2026-11-24, after chat 192): enabled, a new version's filter reading everything
        // reprocesses the whole feed, so both versions' Events exist for its old data; asked each time.
        world.add(PipelineDoc.TYPE, "Azure-Audit-V1.0-EVENTS Messaging V1.0",
                world.folder("System/Feeds/Azure/Messaging/Events/V1.0").uuid, "ev1");
        final DocRef version = world.inBuild(BUILD, PipelineDoc.TYPE, "Azure-Audit-V1.0-EVENTS Messaging V1.1",
                "ev2");
        world.records().update(BUILD, version, entry -> entry.put("version_of", "ev1"));
        final Map<String, Object> listed = FakeBuildFacts.doc(PipelineDoc.TYPE, "ev2",
                "Azure-Audit-V1.0-EVENTS Messaging V1.1", "version_of", "ev1");
        listed.put("role", "Events");
        docs.facts.docs.add(listed);
        Mockito.when(processing.promotionProcessing(any(), any(), any(), any())).thenReturn(List.of(
                new PromotionProcessing.Entry(version, List.of("Azure-Audit-V1.0-EVENTS"), "Raw Events",
                        List.of())));
        final Map<String, Object> destinations = Map.of();
        final Map<String, Object> shown = call(destinations, null);

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, "choice",
                "From now on (recommended): streams arriving after promotion"));
        final long before = System.currentTimeMillis();
        final Map<String, Object> promoted = docs.call(tool, user.context(),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(user.shown()).anySatisfy(form -> assertThat(form).contains("Which streams does the new "
                                                                              + "version's filter read?"));
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Long>> since = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(processing).createPromotionFilters(any(), any(), any(), since.capture());
        assertThat(since.getValue()).containsOnlyKeys("ev2");
        assertThat(since.getValue().get("ev2")).isBetween(before, System.currentTimeMillis());
        assertThat(String.valueOf(promoted.get("new_version_reads"))).contains("streams arriving from promotion "
                                                                               + "on", "as the user chose");
    }

    @Test
    void aFollowOnIndexingTheNewEventsTwiceIsPassedOverAndSaidInTheReviewTheFormAndTheResult() {
        // Seen in an eval (case 61): the production indexing pipeline gained a follow-on filter reading the V1.1
        // Events while the build's own indexing pipeline indexed them into the same index.
        final DocRef version = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.1-Events", "ev2");
        world.add(PipelineDoc.TYPE, "ACME-V1.0-Events", world.folder("System/Feeds/Acme/Events/V1.0").uuid, "ev1");
        world.records().update(BUILD, version, entry -> entry.put("version_of", "ev1"));
        final Map<String, Object> listed = FakeBuildFacts.doc(PipelineDoc.TYPE, "ev2", "ACME-V1.1-Events", "version_of",
                "ev1");
        listed.put("role", "Events");
        docs.facts.docs.add(listed);
        final DocRef indexing = new DocRef(PipelineDoc.TYPE, "ip", "ACME - Indexing");
        final PromotionProcessing.FollowOn followOn = new PromotionProcessing.FollowOn(indexing,
                ProcessorFilter.builder().id(5).pipelineUuid("ip").enabled(true).build(),
                new DocRef(PipelineDoc.TYPE, "ev1", "ACME-V1.0-Events"), version);
        Mockito.when(processing.followOns(any(), any())).thenReturn(List.of(followOn));
        final String said = "'ACME - Indexing': no new filter, as 'ACME - Indexing V1.1' already indexes the Events "
                            + "of 'ACME-V1.1-Events' into the same index";
        Mockito.when(processing.withoutDoubleIndexing(any(), any(), any())).thenReturn(
                new PromotionProcessing.Followed(List.of(), List.of(said)));
        Mockito.when(processing.createPromotionFilters(any(), any(), any(), any())).thenReturn(List.of());
        final Map<String, Object> destinations = Map.of();

        final Map<String, Object> shown = call(destinations, null);

        assertThat(shown).doesNotContainKey("follow_on_filters")
                .containsEntry("follow_on_filters_passed_over", List.of(said));
        assertThat(String.valueOf(approvalForm(destinations, shown.get("review_id"))))
                .contains("follow-on filters not made");
        final Map<String, Object> promoted = docs.call(tool, BuildToolCalls.answering(Map.of("value", true)),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));
        assertThat(promoted).doesNotContainKey("follow_on_filters")
                .containsEntry("follow_on_filters_passed_over", List.of(said));
        Mockito.verify(processing, Mockito.never()).createFollowOnFilters(any(), any(), any());
    }

    @Test
    void aFollowOnWithItsOwnNewVersionInTheBuildGainsNoFilterReadingTheNewEvents() {
        // A build making new versions of both the events pipeline and the CEF flattener reading its Events: the CEF
        // flattener's new version reads the new Events by its own filter, so its predecessor gains none (two filters
        // would make the CEF output twice once both were enabled).
        world.add(PipelineDoc.TYPE, "ACME-V1.0-Events", world.folder("System/Feeds/Acme/Events/V1.0").uuid, "ev1");
        world.add(PipelineDoc.TYPE, "ACME-V1.0-CEF", world.folder("System/Feeds/Acme/CEF/V1.0").uuid, "cef1");
        final DocRef events = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.1-Events", "ev2");
        world.records().update(BUILD, events, entry -> entry.put("version_of", "ev1"));
        final DocRef cef = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.1-CEF", "cef2");
        world.records().update(BUILD, cef, entry -> entry.put("version_of", "cef1"));
        final Map<String, Object> listedEvents = FakeBuildFacts.doc(PipelineDoc.TYPE, "ev2", "ACME-V1.1-Events",
                "version_of", "ev1");
        listedEvents.put("role", "Events");
        docs.facts.docs.add(listedEvents);
        final Map<String, Object> listedCef = FakeBuildFacts.doc(PipelineDoc.TYPE, "cef2", "ACME-V1.1-CEF",
                "version_of", "cef1");
        listedCef.put("role", "CEF");
        docs.facts.docs.add(listedCef);
        @SuppressWarnings("unchecked") final ArgumentCaptor<Predicate<DocRef>> outside =
                ArgumentCaptor.forClass(Predicate.class);
        Mockito.when(processing.followOns(any(), outside.capture())).thenReturn(List.of());

        call(Map.of(), null);

        assertThat(outside.getValue().test(new DocRef(PipelineDoc.TYPE, "cef1", "ACME-V1.0-CEF"))).isFalse();
        assertThat(outside.getValue().test(new DocRef(PipelineDoc.TYPE, "other", "OTHER-Indexing"))).isTrue();
    }

    @Test
    void inTheChatPromotionOffersStandingInstructionsFromTheUsersChats() {
        exists("System/Feeds/Acme");
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef documentation = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> shown = docs.call(tool, user.chatContext(),
                DocumentationWorld.args(args(destinations, null)));
        final Map<String, Object> promoted = docs.call(tool, user.chatContext(),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(world.documentAccess.path(documentation)).isEqualTo("/Feeds/Acme");
        assertThat(String.valueOf(promoted.get("standing_instructions")))
                .contains("call instruction_evidence", "offer once", "propose_instructions", "Don't offer if it "
                                                                                           + "finds none");
    }

    @Test
    void itAsksTheUserItselfBeforeEveryChange() {
        // promote_build is no read-only tool, and confirms itself, so /mcp doesn't approve the call as a whole as
        // well.
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isTrue();
        assertThat(tool.definition().description()).startsWith("Onboarding step 18 of 18 (promoted): ");
    }

    // A build with test data, and the destinations to promote it with: its test feed (2 streams, 3 KiB), its Lucene
    // test index (3 shards) standing in for a production index, the -VERIFY dashboard on it, a pipeline and its
    // documentation to promote, its survey doc, and a production feed and pipeline beside where it goes.
    private Map<String, Object> buildWithTestData() {
        exists("System/Feeds/Acme");
        final String production = world.folder("System/Feeds/Acme").uuid;
        world.add(FeedDoc.TYPE, "ACME", production, "prod-feed");
        world.add(PipelineDoc.TYPE, "ACME-Prod", production, "prod-p");
        world.inBuild(BUILD, FeedDoc.TYPE, "ACME" + OutputFeeds.TEST_FEED, "t");
        final DocRef index = world.inBuild(BUILD, LuceneIndexDoc.TYPE, "ACME-INDEX" + BuildTestIndexes.TEST_INDEX,
                "ti");
        world.records().update(BUILD, index, entry -> entry.put(BuildTestIndexes.TEST_INDEX_FOR, "prod-ix"));
        world.inBuild(BUILD, DashboardDoc.TYPE, "ACME-INDEX-MCP-TEST-VERIFY", "vd");
        world.docs.put("vd", DashboardDoc.builder().uuid("vd").name("ACME-INDEX-MCP-TEST-VERIFY").version("v0")
                .dashboardConfig(DashboardConfig.builder().components(List.of(ComponentConfig.builder()
                        .type("query").id("query").name("Query")
                        .settings(QueryComponentSettings.builder()
                                .dataSource(new DocRef(LuceneIndexDoc.TYPE, "ti")).build())
                        .build())).build())
                .build());
        world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME - Survey", "s");
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        listed(doc(FeedDoc.TYPE, "t", "ACME" + OutputFeeds.TEST_FEED, null),
                doc(LuceneIndexDoc.TYPE, "ti", "ACME-INDEX" + BuildTestIndexes.TEST_INDEX, null),
                doc(DashboardDoc.TYPE, "vd", "ACME-INDEX-MCP-TEST-VERIFY", null),
                doc(DocumentationDoc.TYPE, "s", "ACME - Survey", null),
                doc(PipelineDoc.TYPE, "p", "ACME-Events", null),
                doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        final BuildTestIndexes testIndexes = Mockito.mock(BuildTestIndexes.class);
        Mockito.when(testIndexes.testIndexes(BUILD)).thenReturn(Map.of("ti", "prod-ix"));
        tool.setTestIndexes(() -> testIndexes);
        Mockito.when(metaService.findRows(any())).thenReturn(new ResultPage<>(List.of(row(1, "1024"),
                row(2, "2048"))));
        Mockito.when(metaService.updateStatus(any(FindMetaCriteria.class), Mockito.<Status>isNull(),
                Mockito.eq(Status.DELETED))).thenReturn(2);
        Mockito.when(indexResource.find(any())).thenReturn(new ResultPage<>(List.of(shard(1), shard(2), shard(3))));
        Mockito.when(indexResource.reset("ti")).thenReturn(3L);
        return Map.of("Pipeline", "/Feeds/Acme");
    }

    // Reviewed, then approved in the user's form: the promotion's result.
    private Map<String, Object> promote(final BuildToolCalls.FormUser user, final Map<String, Object> destinations) {
        final Map<String, Object> shown = docs.call(tool, user.context(), DocumentationWorld.args(args(destinations,
                null)));
        return docs.call(tool, user.context(), DocumentationWorld.args(args(destinations, shown.get("review_id"))));
    }

    // A stream of the size given (its File Size attribute).
    private static MetaRow row(final long id, final String size) {
        return new MetaRow(Meta.builder().id(id).build(), null, Map.of(MetaFields.FILE_SIZE.getFldName(), size));
    }

    private static IndexShard shard(final long id) {
        return IndexShard.builder().id(id).indexUuid("ti").status(IndexShard.IndexShardStatus.CLOSED).build();
    }

    private Map<String, Object> review(final Map<String, Object> destinations) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("build", BUILD);
        args.put("destinations", destinations);
        final Map<String, Object> shown = docs.call(tool, BuildToolCalls.context(), DocumentationWorld.args(args));
        assertThat(shown).containsEntry("status", "needs_review");
        return shown;
    }

    // A call from a client without forms: the layout to review, or the refusal to promote without a form.
    // A call's context in one chat, its workspace the one given (what the chat remembers between calls).
    private static ToolCallContext inChat(final UserQuestions questions, final SessionWorkspace workspace) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(questions)
                .workspace(workspace)
                .build();
    }

    // A chat's workspace, its files kept in memory.
    private static SessionWorkspace memoryWorkspace() {
        final Map<String, String> files = new LinkedHashMap<>();
        final ChatWorkspace chat = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chat.write(Mockito.anyInt(), any(WorkspacePath.class), Mockito.anyString(), any()))
                .thenAnswer(inv -> {
                    files.put(String.valueOf((Object) inv.getArgument(1)), inv.getArgument(2));
                    return files.size();
                });
        Mockito.when(chat.find(Mockito.anyInt(), any(WorkspacePath.class))).thenAnswer(inv ->
                files.containsKey(String.valueOf((Object) inv.getArgument(1)))
                        ? Optional.of(1)
                        : Optional.empty());
        Mockito.when(chat.readLines(Mockito.anyInt(), any(WorkspacePath.class), Mockito.anyLong(),
                Mockito.anyInt())).thenAnswer(inv ->
                List.of(files.get(String.valueOf((Object) inv.getArgument(1))).split("\n", -1)));
        return new SessionWorkspace(chat, 1);
    }

    private Map<String, Object> call(final Map<String, Object> destinations, final Object reviewed) {
        return docs.call(tool, BuildToolCalls.context(), DocumentationWorld.args(args(destinations, reviewed)));
    }

    // The approval form a reviewed call shows, as the round it once was; the user declines it, so nothing is
    // promoted.
    private Map<String, Object> approvalForm(final Map<String, Object> destinations, final Object reviewed) {
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        assertThat(docs.refusal(tool, user.context(), DocumentationWorld.args(args(destinations, reviewed))))
                .startsWith("The user did not agree to: Promote build");
        assertThat(user.forms()).hasSize(1);
        return user.forms().getFirst();
    }

    private static Map<String, Object> args(final Map<String, Object> destinations, final Object reviewed) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("build", BUILD);
        args.put("destinations", destinations);
        if (reviewed != null) {
            args.put("reviewed", reviewed);
        }
        return args;
    }

    // A module XSLT in the build, marked as the generator marks it.
    private void module(final String uuid, final String name, final String kind, final String group) {
        world.inBuild(BUILD, XsltDoc.TYPE, name, uuid);
        world.docs.put(uuid, XsltDoc.builder().uuid(uuid).name(name).version("v0")
                .data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:mcp=\""
                      + MappingFacts.MCP_NS + "\" version=\"3.0\"><mcp:module kind=\"" + kind + "\""
                      + (group == null
                        ? ""
                        : " name=\"" + group + "\" mode=\"m365\"") + "/></xsl:stylesheet>")
                .build());
    }

    // A feed's common module, marked as the generator marks it, with the text given.
    private static String commonModule(final String text) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:mcp=\"" + MappingFacts.MCP_NS
               + "\" version=\"3.0\"><mcp:module kind=\"common\"/>\n<xsl:variable name=\"v\">" + text
               + "</xsl:variable>\n</xsl:stylesheet>";
    }

    // A sibling feed beside ACME's folder (System/Feeds/Acme), its events pipeline under its own folder and its
    // indexing pipeline under System/Elastic Indices.
    private void siblingWithIndexingElsewhere() {
        exists("System/Feeds/Acme", "System/Elastic Indices/Beta/V1.0", "System/Feeds/Beta/Events/V1.0");
        world.add(FeedDoc.TYPE, "BETA", world.folder("System/Feeds/Beta").uuid, "beta");
        Mockito.when(docs.pipelineIndex.read()).thenReturn(Map.of(
                "beta-ev", new PipelineIndex.Entry("beta-ev", "BETA-V1.0-Events", "System/Feeds/Beta/Events/V1.0",
                        "template", null),
                "beta-ix", new PipelineIndex.Entry("beta-ix", "BETA-V1.0-Indexing", "System/Elastic Indices/Beta/V1.0",
                        "template", null)));
        docs.facts.stages.put("beta-ev", "translation");
        docs.facts.stages.put("beta-ix", "indexing");
    }

    // Production feed ABC-FEED in a folder named after it, the build's pipelines working from it: the feed's folder.
    private String abcFeed() {
        final String own = "System/Feeds and Translations/ABC/ABC-FEED";
        world.add(FeedDoc.TYPE, "ABC-FEED", world.folder(own).uuid, "abc");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "ABC-FEED", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "abc", "ABC-FEED")));
        docs.facts.sources = List.of("ABC-FEED");
        listed(doc(PipelineDoc.TYPE, "ev", "ABC-FEED-V1.0-EVENTS", "Events"),
                doc(PipelineDoc.TYPE, "ix", "ABC-FEED-V1.0-INDEXING", "Indexing"));
        return own;
    }

    // A build whose pipeline uses its new splitter and XSLT (which imports its helpers and reads a dictionary
    // importing another), beside the splitter it replaced (with its documentation) and an XSLT nothing imports.
    private Map<String, Object> buildWithAReplacedSplitter() {
        exists("System/Feeds/Acme");
        world.inBuild(BUILD, FeedDoc.TYPE, "ACME", "f");
        world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-V1.0-Events", "ev");
        world.inBuild(BUILD, TextConverterDoc.TYPE, "ACME-V1.0-Splitter", "ds");
        world.inBuild(BUILD, TextConverterDoc.TYPE, "ACME-V1.0-Splitter-OLD", "old");
        world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-V1.0-Splitter-OLD", "old-doc");
        world.inBuild(BUILD, XsltDoc.TYPE, "ACME-V1.0-Events", "x");
        world.inBuild(BUILD, XsltDoc.TYPE, "ACME Helpers", "h");
        world.inBuild(BUILD, XsltDoc.TYPE, "ACME Unused", "unused");
        world.inBuild(BUILD, DictionaryDoc.TYPE, "ACME Words", "dict");
        world.inBuild(BUILD, DictionaryDoc.TYPE, "ACME Lists", "lists");
        world.docs.put("x", XsltDoc.builder().uuid("x").name("ACME-V1.0-Events").version("v0")
                .data("<xsl:stylesheet><xsl:import href=\"ACME Helpers\"/>"
                      + "<xsl:variable name=\"w\" select=\"stroom:dictionary('ACME Words')\"/></xsl:stylesheet>")
                .build());
        world.docs.put("dict", DictionaryDoc.builder().uuid("dict").name("ACME Words").version("v0")
                .imports(List.of(new DocRef(DictionaryDoc.TYPE, "lists", "ACME Lists"))).build());
        world.docs.put("ev", PipelineDoc.builder().uuid("ev").name("ACME-V1.0-Events").version("v0")
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(new PipelineProperty("dsParser", "textConverter", new PipelinePropertyValue(
                                new DocRef(TextConverterDoc.TYPE, "ds", "ACME-V1.0-Splitter"))))
                        .addProperty(new PipelineProperty("translationFilter", "xslt", new PipelinePropertyValue(
                                new DocRef(XsltDoc.TYPE, "x", "ACME-V1.0-Events"))))
                        .build())
                .build());
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events", "Events"),
                doc(TextConverterDoc.TYPE, "ds", "ACME-V1.0-Splitter", "Events"),
                doc(TextConverterDoc.TYPE, "old", "ACME-V1.0-Splitter-OLD", "Events"),
                doc(DocumentationDoc.TYPE, "old-doc", "ACME-V1.0-Splitter-OLD", "Events"),
                doc(XsltDoc.TYPE, "x", "ACME-V1.0-Events", "Events"), doc(XsltDoc.TYPE, "h", "ACME Helpers", "Events"),
                doc(XsltDoc.TYPE, "unused", "ACME Unused", "Events"),
                doc(DictionaryDoc.TYPE, "dict", "ACME Words", "Events"),
                doc(DictionaryDoc.TYPE, "lists", "ACME Lists", "Events"));
        return Map.of("Feed", "/Feeds/Acme");
    }

    // A call's context from an MCP client whose forms the user given answers.
    private static ToolCallContext asking(final UserQuestions questions) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(questions)
                .build();
    }

    // Chat 194's build: production feed ENDPOINT-LOGS-V1.0-EVENTS in System/Feeds/Endpoint, a sibling feed's
    // pipelines in its own role folders under System/Feeds, and the build's Events and Indexing pipelines.
    private void endpointFeed() {
        final String own = "System/Feeds/Endpoint";
        world.add(FeedDoc.TYPE, "ENDPOINT-LOGS-V1.0-EVENTS", world.folder(own).uuid, "endpoint");
        Mockito.when(docs.pipelineIndex.named(FeedDoc.TYPE, "ENDPOINT-LOGS-V1.0-EVENTS", 20))
                .thenReturn(List.of(new DocRef(FeedDoc.TYPE, "endpoint", "ENDPOINT-LOGS-V1.0-EVENTS")));
        docs.facts.sources = List.of("ENDPOINT-LOGS-V1.0-EVENTS");
        exists("System/Feeds/Firewall/Events/V1.0", "System/Feeds/Firewall/Indexing/V1.0");
        world.add(FeedDoc.TYPE, "FIREWALL", world.folder("System/Feeds/Firewall").uuid, "firewall");
        Mockito.when(docs.pipelineIndex.read()).thenReturn(Map.of(
                "fw-ev", new PipelineIndex.Entry("fw-ev", "FIREWALL-V1.0-Events", "System/Feeds/Firewall/Events/V1.0",
                        "template", null),
                "fw-ix", new PipelineIndex.Entry("fw-ix", "FIREWALL-V1.0-Indexing",
                        "System/Feeds/Firewall/Indexing/V1.0", "template", null)));
        docs.facts.stages.put("fw-ev", "translation");
        docs.facts.stages.put("fw-ix", "indexing");
        listed(doc(PipelineDoc.TYPE, "ev", "Endpoint-Logs-V1.0-Events", "Events"),
                doc(PipelineDoc.TYPE, "ix", "Endpoint-Logs-V1.0-Indexing", "Indexing"));
    }

    // A form's answer: every choice the option given (the first, for null), every text field the text given.
    private static ElicitResult answerAll(final Map<String, Object> schema, final String option, final String typed) {
        final Map<String, Object> content = new LinkedHashMap<>();
        asMap(schema.get("properties")).forEach((key, field) -> {
            final Object options = asMap(field).get("enum");
            if (options instanceof final List<?> list) {
                content.put(key, option == null
                        ? list.getFirst()
                        : option);
            } else if (typed != null) {
                content.put(key, typed);
            }
        });
        return new ElicitResult(ElicitResult.Action.ACCEPT, content);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(final Object value) {
        return (List<Object>) value;
    }

    // The folders, made.
    private void exists(final String... folders) {
        for (final String folder : folders) {
            world.folder(folder);
        }
    }

    @SafeVarargs
    private void listed(final Map<String, Object>... listedDocs) {
        docs.facts.docs.addAll(List.of(listedDocs));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> doc(final String type, final String uuid, final String name, final String role) {
        return FakeBuildFacts.doc(type, uuid, name, "role", role);
    }


    // --------------------------------------------------------------------------------


    /// A user of a client that shows forms, answering each form in turn as given (the last answer again after the
    /// rest), keeping each form's message and fields.
    private static final class SequencedUser implements UserQuestions {

        private final List<BiFunction<Map<String, Object>, String, ElicitResult>> answers;
        private final List<String> intros = new ArrayList<>();
        private final List<Map<String, Object>> schemas = new ArrayList<>();

        private SequencedUser(final List<BiFunction<Map<String, Object>, String, ElicitResult>> answers) {
            this.answers = answers;
        }

        List<String> intros() {
            return intros;
        }

        List<Map<String, Object>> schemas() {
            return schemas;
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            intros.add(intro);
            schemas.add(form);
            return answers.get(Math.min(answers.size(), intros.size()) - 1).apply(form, intro);
        }
    }
}
