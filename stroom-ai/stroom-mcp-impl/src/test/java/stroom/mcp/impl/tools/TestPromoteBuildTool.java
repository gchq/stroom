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

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// promote_build (the Stroom MCP server's `tools/builds.py` `promote_build` and `consolidate_versions`, and their
/// tests in `tests/test_promotion_layout.py` and `tests/test_surface.py`, with what `dev/e2e_plan_walk.py` and
/// `dev/e2e_source_docs.py` expect of it).
class TestPromoteBuildTool {

    private static final String BUILD = "bb";

    private final DocumentationWorld docs = new DocumentationWorld();
    private final BuildWorld world = docs.world;
    private final PromotionProcessing processing = Mockito.mock(PromotionProcessing.class);
    private final PromoteBuildTool tool = new PromoteBuildTool(docs.facts, new PromotionLayout(docs.places),
            docs.places, world.guard(), world.records(), docs.translation.consent, processing,
            Mockito.mock(ProcessorFilterWrites.class), Mockito.mock(IndexingRuns.class),
            Mockito.mock(OutputFeeds.class), docs.writes, world.documentAccess, docs.translation.history);

    @Test
    void roleSubfoldersAreKeptUnderEachDestinationUnlessTheUserSaysNot() {
        // Asked for by the user: the build's Events and Indexing subfolders in production too, as they confirm.
        exists("System/Feeds/Acme/Events", "System/Feeds/Acme/Indexing", "System/Elastic Indices/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME", null), doc(PipelineDoc.TYPE, "ev", "ACME-Events", "Events"),
                doc(XsltDoc.TYPE, "x", "ACME-Events", "Events"), doc(PipelineDoc.TYPE, "ix", "ACME-Indexing",
                        "Indexing"));
        final Map<String, Object> destinations = Map.of("Feed", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme",
                "XSLT", "System/Feeds/Acme");

        final Map<String, Object> shown = review(destinations, null);

        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME  (Feed)\n"
                                                  + "├── Events/\n"
                                                  + "│   ├── ACME-Events  (Pipeline)\n"
                                                  + "│   └── ACME-Events  (XSLT)\n"
                                                  + "└── Indexing/\n"
                                                  + "    └── ACME-Indexing  (Pipeline)");
        assertThat(String.valueOf(shown.get("hint"))).contains("whether to keep the build's role subfolders")
                .contains("role_subfolders");

        final Map<String, Object> flat = review(destinations, false);
        assertThat(String.valueOf(flat.get("layout"))).doesNotContain("Events/").doesNotContain("Indexing/");

        // A folder given for one document is where it goes, as given.
        final Map<String, Object> given = new LinkedHashMap<>(destinations);
        given.put("ix", "System/Elastic Indices/Acme");
        assertThat(String.valueOf(review(given, null).get("layout")))
                .contains("Elastic Indices/\n    └── Acme/\n        └── ACME-Indexing  (Pipeline)");
    }

    @Test
    void aBuildOnAnExistingFeedLeavesItsTestFeedInTheWorkspace() {
        // Seen in VS Code: onboarding from a feed already in Stroom, the agent copied records into a test feed
        // (named like any other), and promotion would have made it production with the pipeline's filter selecting
        // it.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME-COPY", null), doc(PipelineDoc.TYPE, "ev", "ACME-Events", "Events"));
        docs.facts.sources = List.of("ACME");

        assertThat(String.valueOf(review(Map.of("Feed", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme"), null)
                .get("layout"))).endsWith("Left in the workspace: ACME-COPY (Feed)");
        // Promoted when the user gives it a folder of its own (a new version of the feed, say).
        assertThat(String.valueOf(review(Map.of("f", "System/Feeds/Acme", "Pipeline", "System/Feeds/Acme"), null)
                .get("layout"))).doesNotContain("Left in the workspace");
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

        assertThat(review(Map.of(), false).get("layout")).isEqualTo("System/Indices/Acme/V2/\n"
                                                                    + "├── ACME-V2-Indexing  (Pipeline)\n"
                                                                    + "└── ACME-VERIFY  (Dashboard)");
    }

    @Test
    void aBuildOfOneRoleGetsNoRoleSubfolder() {
        // An index documented, or a new indexing version: all of it Indexing, so a subfolder would only add a
        // level (and put a doc the user placed in a folder of their choosing one level down).
        exists("System/Docs");
        listed(doc(DocumentationDoc.TYPE, "d", "ACME-Index", "Indexing"), doc(PipelineDoc.TYPE, "ix",
                "ACME-Indexing", "Indexing"));

        final Map<String, Object> shown = review(Map.of("Documentation", "System/Docs", "Pipeline", "System/Docs"),
                null);

        assertThat(String.valueOf(shown.get("layout"))).doesNotContain("Indexing/");
        assertThat(String.valueOf(shown.get("hint"))).doesNotContain("role_subfolders");
    }

    @Test
    void aBuildWithNoDocumentsIsRefused() {
        assertThat(docs.refusal(tool, PortedToolCalls.context(), "{\"build\": \"bb\"}"))
                .isEqualTo("Build 'bb' has no documents");
    }

    @Test
    void aPipelinesDocumentationFollowsThePipelinePromotedWithIt() {
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events",
                null));

        final Map<String, Object> shown = review(Map.of("Pipeline", "System/Feeds/Acme"), null);

        // Every doc had a destination: on to the folders, to be made.
        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/  (new)\n"
                                                  + "├── ACME-Events  (Documentation)\n"
                                                  + "└── ACME-Events  (Pipeline)");
    }

    @Test
    void theSourcesNotesAndReferenceDocsGoBesideTheFeed() {
        // dev/e2e_source_docs.py: promoted beside the feed, where later work on it looks.
        exists("System/Feeds/Acme");
        listed(doc(FeedDoc.TYPE, "f", "ACME-DIR", null), doc(DocumentationDoc.TYPE, "n",
                "Acme directory source notes", null), doc(DocumentationDoc.TYPE, "r",
                "Acme directory reference - audit log reference", null));

        assertThat(review(Map.of("Feed", "System/Feeds/Acme"), null).get("layout"))
                .isEqualTo("System/Feeds/Acme/\n"
                           + "├── ACME-DIR  (Feed)\n"
                           + "├── Acme directory reference - audit log reference  (Documentation)\n"
                           + "└── Acme directory source notes  (Documentation)");
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
        assertThat(String.valueOf(asked.get("layout"))).startsWith("1 document(s), as shown in the chat")
                .contains("(" + shown.get("review_id") + ")");
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
    void everyDocumentWithoutADestinationIsNamedAtOnce() {
        // Seen (Delinea SecretServer): the Dashboard's, then the TextConverter's, a failed call each.
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DashboardDoc.TYPE, "d", "ACME-VERIFY", null),
                doc(TextConverterDoc.TYPE, "t", "ACME wrapper", null));

        final String said = docs.refusal(tool, PortedToolCalls.context(), DocumentationWorld.args(Map.of("build",
                BUILD, "destinations", Map.of("XSLT", "Feeds/Acme"))));

        assertThat(said).contains("Pipeline 'ACME-Events' (p); Dashboard 'ACME-VERIFY' (d); TextConverter 'ACME "
                                  + "wrapper' (t)")
                .contains("by type (Dashboard, Pipeline, TextConverter")
                .contains("'keep'")
                .endsWith("Nothing was changed.");
    }

    @Test
    void documentationTheBuildsOwnChangeLeftBehindHoldsPromotionUpAndAHandEditOnlyWarns() {
        // dev/e2e_plan_walk.py: written before the build's change, the documentation holds promotion up, naming
        // the call; after a hand edit of the XSLT, it is a warning in the approval.
        exists("System/Feeds/Acme");
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null));
        final String stale = "Pipeline 'ACME-Events': the documentation's Field mapping predates the current mapping "
                             + "or XSLT (write_documentation build=<this build> pipeline_uuid='p' "
                             + "stream_ids=<its sample streams> change=<what changed>)";
        docs.facts.checks = List.of(stale + BuildChecks.BLOCKS);

        assertThat(docs.refusal(tool, PortedToolCalls.context(), DocumentationWorld.args(Map.of("build", BUILD,
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
    void promotionMovesTheBuildIntoPlaceAndWritesItsWorkingCopiesBackAfterABackup() {
        exists("System/Feeds/Acme");
        final String prodFolder = world.folder("System/Feeds/Acme").uuid;
        world.add(XsltDoc.TYPE, "ACME-XSLT", prodFolder, "x-prod");
        world.docs.put("x-prod", XsltDoc.builder().uuid("x-prod").name("ACME-XSLT").version("v0")
                .data("<xsl:stylesheet>v1</xsl:stylesheet>").build());
        final DocRef pipeline = world.inBuild(BUILD, PipelineDoc.TYPE, "ACME-Events", "p");
        final DocRef documentation = world.inBuild(BUILD, DocumentationDoc.TYPE, "ACME-Events", "d");
        world.docs.put("d", ((DocumentationDoc) world.docs.get("d")).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nDoors.", "", "Created", "jane (agent)",
                        "", Instant.parse("2026-10-20T00:00:00Z"))).build());
        final DocRef copy = world.inBuild(BUILD, XsltDoc.TYPE, "ACME-XSLT", "x-copy");
        world.docs.put("x-copy", XsltDoc.builder().uuid("x-copy").name("ACME-XSLT").version("v0")
                .data("<xsl:stylesheet>v2</xsl:stylesheet>").build());
        world.records().update(BUILD, copy, entry -> entry.put("copy_of", "x-prod"));
        listed(doc(PipelineDoc.TYPE, "p", "ACME-Events", null), doc(DocumentationDoc.TYPE, "d", "ACME-Events", null));
        docs.facts.docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "x-copy", "ACME-XSLT", "working_copy_of", "x-prod"));
        final Map<String, Object> destinations = Map.of("Pipeline", "/Feeds/Acme");

        final Map<String, Object> shown = call(destinations, null);
        assertThat(shown.get("layout")).isEqualTo("System/Feeds/Acme/\n"
                                                  + "├── ACME-Events  (Documentation)\n"
                                                  + "├── ACME-Events  (Pipeline)\n"
                                                  + "└── ACME-XSLT  (XSLT: updated from its working copy, backed up "
                                                  + "first)");
        assertThat(approvalForm(destinations, shown.get("review_id"))).containsEntry("status", "needs_approval");
        // Nothing changed until the user approves, which they can't without a form.
        assertThat(docs.refusal(tool, PortedToolCalls.context(), DocumentationWorld.args(args(destinations,
                shown.get("review_id"))))).startsWith("Not done: Promote build '" + BUILD + "' as shown in the chat.");
        assertThat(world.guard().buildOf(pipeline)).contains(BUILD);

        final Map<String, Object> promoted = docs.call(tool, PortedToolCalls.answering(Map.of("value", true)),
                DocumentationWorld.args(args(destinations, shown.get("review_id"))));

        assertThat(promoted).containsEntry("layout", shown.get("layout"));
        assertThat(promoted.get("promoted")).asList().contains(
                "recorded the build's changes as version 1 of documentation 'ACME-Events'",
                "wrote XSLT 'ACME-XSLT' back over 'ACME-XSLT' (backup kept)",
                "moved Pipeline 'ACME-Events' to System/Feeds/Acme",
                "moved Documentation 'ACME-Events' to System/Feeds/Acme",
                "removed the build's workspace folder, now empty");
        // Moved into production: no longer the agent's to change, still known as the tools' own.
        assertThat(world.documentAccess.path(pipeline)).isEqualTo("/Feeds/Acme");
        assertThat(world.tags("p")).containsExactly(WriteGuard.GENERATED);
        assertThat(DocumentationVersions.rowsOf(world.body(documentation.getUuid()))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("version", "1").containsEntry("change", "Created"));
        // The working copy written back over its original, after a backup, then deleted.
        assertThat(((XsltDoc) world.docs.get("x-prod")).getData()).isEqualTo("<xsl:stylesheet>v2</xsl:stylesheet>");
        assertThat(world.nodes).doesNotContainKey("x-copy");
        assertThat(world.nodes.values()).anySatisfy(node -> {
            assertThat(node.name).isEqualTo("ACME-XSLT backup 20261022000000");
            assertThat(node.tags).contains(WriteGuard.GENERATED);
            assertThat(world.documentAccess.path(node.docRef())).isEqualTo("/MCP Workspace/backups");
        });
        assertThat(world.guard().buildFolder(BUILD, false)).isEmpty();
        assertThat(((List<?>) promoted.get("updated_in_place")).stream().map(entry -> String.valueOf(((Map<?, ?>) entry)
                .get("document")))).containsExactly("ACME-XSLT (XSLT)");
        assertThat(((List<?>) promoted.get("folders")).stream()
                .map(entry -> String.valueOf(((Map<?, ?>) entry).get("folder"))))
                .containsExactly("System/Feeds/Acme");
    }

    @Test
    void itAsksTheUserItselfBeforeEveryChange() {
        // The Stroom MCP server's tools/toolmeta.py: promote_build is no read-only tool, and confirms itself, so
        // /mcp doesn't approve the call as a whole as well.
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isTrue();
        assertThat(tool.definition().description()).startsWith("Onboarding step 15 of 15 (promoted): ");
    }

    private Map<String, Object> review(final Map<String, Object> destinations, final Boolean roleSubfolders) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("build", BUILD);
        args.put("destinations", destinations);
        if (roleSubfolders != null) {
            args.put("role_subfolders", roleSubfolders);
        }
        final Map<String, Object> shown = docs.call(tool, PortedToolCalls.context(), DocumentationWorld.args(args));
        assertThat(shown).containsEntry("status", "needs_review");
        return shown;
    }

    // A call from a client without forms: the layout to review, or the refusal to promote without a form.
    private Map<String, Object> call(final Map<String, Object> destinations, final Object reviewed) {
        return docs.call(tool, PortedToolCalls.context(), DocumentationWorld.args(args(destinations, reviewed)));
    }

    // The approval form a reviewed call shows, as the round it once was; the user declines it, so nothing is
    // promoted.
    private Map<String, Object> approvalForm(final Map<String, Object> destinations, final Object reviewed) {
        final PortedToolCalls.FormUser user = PortedToolCalls.declining();
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

    private static Map<String, Object> doc(final String type, final String uuid, final String name, final String role) {
        return FakeBuildFacts.doc(type, uuid, name, "role", role);
    }
}
