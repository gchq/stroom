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

import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Promotion's layout (role then version folders, a new version beside its predecessor, the roots sibling feeds use,
/// and the tree shown), from testing in production: where an amended pipeline's documents would go wasn't plain (a
/// list in a form), and a new version went beside the old one with nothing to tell them apart (asked for by the
/// user); role first, then version, decided by the user 2026-11-17.
class TestPromotionLayout {

    private final BuildWorld world = new BuildWorld();
    private final PromotionLayout layout = new PromotionLayout(new DocumentPlaces(world.documentAccess,
            Mockito.mock(PipelineIndex.class)));

    @Test
    void aRolesDocumentsGoInItsRoleThenVersionFolderUnderTheFeedsOwnRoot() {
        // Decided by the user (2026-11-17): role first, then version, always a role folder (even with one role).
        assertThat(PromotionLayout.rolePath("/Parent/App-Logs", null, null, "Events", "V1.0"))
                .isEqualTo("System/Parent/App-Logs/Events/V1.0");
        assertThat(PromotionLayout.rolePath("System/Parent/App-Logs", "System/Parent", null, "Indexing", "V1.0"))
                .isEqualTo("System/Parent/App-Logs/Indexing/V1.0");
        // An aggregated feed's group: its own folder in the feed's.
        assertThat(PromotionLayout.rolePath("System/Parent/App-Logs", null, "Messaging", "Events", "V1.0"))
                .isEqualTo("System/Parent/App-Logs/Messaging/Events/V1.0");
    }

    @Test
    void aRootThatIsTheFeedsOwnFolderIsTheFeedsOwnRoot() {
        // Seen in a user test (2026-11-18): the feed in a folder named after it, its own folder given as the role's
        // root, gave <feed>/<feed>/V1.0, Events and Indexing together with no role folders.
        final String feed = "System/Feeds and Translations/ABC/ABC-FEED";
        assertThat(PromotionLayout.rolePath(feed, feed, null, "Events", "V1.0")).isEqualTo(feed + "/Events/V1.0");
        assertThat(PromotionLayout.rolePath(feed, "/Feeds and Translations/ABC/abc-feed", null, "Indexing", "V1.0"))
                .isEqualTo(feed + "/Indexing/V1.0");
        assertThat(PromotionLayout.rolePath(feed, "System/Feeds and Translations/ABC", null, "Indexing", "V1.0"))
                .isEqualTo(feed + "/Indexing/V1.0");
    }

    @Test
    void aNewVersionWhoseRootIsTheFeedsOwnFolderGoesBesideItsPredecessor() {
        // The predecessor's root compared with the feed's own folder given as the role's root: the feed's own.
        final String feed = "System/Feeds/ABC/ABC-FEED";
        world.add(PipelineDoc.TYPE, "ABC-FEED-V1.0-EVENTS", world.folder(feed + "/Events/V1.0").uuid, "before");
        final List<PromotionLayout.Step> plan = new ArrayList<>(List.of(new PromotionLayout.Step(
                FakeBuildFacts.doc(PipelineDoc.TYPE, "new", "ABC-FEED-V1.1-EVENTS", "version_of", "before"),
                PromotionLayout.MOVE, feed, "Events")));

        layout.layOut(plan, Map.of("Events", feed), feed, null, step -> null, List.of("ABC-FEED"));

        assertThat(plan.getFirst().target()).isEqualTo(feed + "/Events/V1.1");
    }

    @Test
    void aRoleWhoseRootIsElsewhereHasNoRoleFolder() {
        // Decided by the user (2026-11-17): Indexing under /System/Elastic Indices gives
        // /System/Elastic Indices/App-Logs/V1.0.
        assertThat(PromotionLayout.rolePath("System/Parent/App-Logs", "/System/Elastic Indices", null, "Indexing",
                "V1.0")).isEqualTo("System/Elastic Indices/App-Logs/V1.0");
        assertThat(PromotionLayout.rolePath("System/Parent/App-Logs", "Elastic Indices", "Messaging", "Indexing",
                "V1.1")).isEqualTo("System/Elastic Indices/App-Logs/Messaging/V1.1");
        // A shared module stays under the feed, whatever root is given.
        assertThat(PromotionLayout.rolePath("System/Parent/App-Logs", "Elsewhere", null, "Common", "V1.1"))
                .isEqualTo("System/Parent/App-Logs/Common/V1.1");
    }

    @Test
    void aNewVersionGoesInASiblingVersionFolderWhateverShapeItsPredecessorHas() {
        // Decided by the user (2026-11-17): the predecessor's version folder off, then its role folder; older
        // layouts tolerated (lower-case version folders exist in e2e), never moved.
        final String feed = "System/Parent/App-Logs";
        assertThat(next("System/Parent/App-Logs/Events/V1.0", feed)).isEqualTo(feed + "/Events/V1.1");
        assertThat(next("System/Parent/App-Logs/Events", feed)).isEqualTo(feed + "/Events/V1.1");
        assertThat(next("System/Parent/App-Logs/V1.0/Events", feed)).isEqualTo(feed + "/Events/V1.1");
        assertThat(next("System/Parent/App-Logs/Events/v2", feed)).isEqualTo(feed + "/Events/V1.1");
        assertThat(next("System/Parent/App-Logs", feed)).isEqualTo(feed + "/Events/V1.1");
        // A group's folder in the feed's.
        assertThat(next("System/Parent/App-Logs/Messaging/Events/V1.0", feed))
                .isEqualTo(feed + "/Messaging/Events/V1.1");
        assertThat(next("System/Parent/App-Logs/Messaging", feed)).isEqualTo(feed + "/Messaging/Events/V1.1");
        // A role's own root elsewhere: no role folder.
        assertThat(next("System/Elastic Indices/App-Logs/V1.0", feed))
                .isEqualTo("System/Elastic Indices/App-Logs/V1.1");
        // The feed's folder unknown: a role folder only where the predecessor had one, or no version folder.
        assertThat(next("System/Elastic Indices/App-Logs/V1.0", null))
                .isEqualTo("System/Elastic Indices/App-Logs/V1.1");
        assertThat(next("System/Feeds/Acme", null)).isEqualTo("System/Feeds/Acme/Events/V1.1");
    }

    @Test
    void aPredecessorsRootIsTheFolderAboveItsFeedsFolder() {
        final String feed = "System/Parent/App-Logs";
        assertThat(root("System/Parent/App-Logs/Events/V1.0", feed, null)).isEqualTo("System/Parent");
        assertThat(root("System/Elastic Indices/App-Logs/V1.0", feed, null)).isEqualTo("System/Elastic Indices");
        assertThat(root("System/Parent/App-Logs/Messaging/Events/V1.0", feed, "Messaging"))
                .isEqualTo("System/Parent");
        assertThat(root("System/Elastic Indices/App-Logs/Messaging/V1.0", feed, "Messaging"))
                .isEqualTo("System/Elastic Indices");
    }

    @Test
    void theSiblingFeedsPipelinesOfARoleTellWhereItGoes() {
        // Decided by the user (2026-11-17): proposed where sibling feeds' pipelines of that role already live, the
        // common parent above their <feed>/<Role>/V.. or <feed>/V.. folders; a pipeline in a folder named for none of
        // them tells nothing.
        final List<PipelineIndex.Entry> pipelines = List.of(
                entry("a-ev", "System/Parent/Alpha/Events/V1.0"),
                entry("a-ix", "System/Elastic Indices/Alpha/V1.0"),
                entry("b-ix", "System/Elastic Indices/Beta/V2.1"),
                entry("c-ix", "System/Parent/Gamma/Indexing/V1.0"),
                entry("other-ix", "System/Somewhere/Unrelated/V1.0"),
                entry("skipped", "System/MCP Workspace/Alpha/Events"));
        final Map<String, String> roles = Map.of("a-ev", "Events", "a-ix", "Indexing", "b-ix", "Indexing",
                "c-ix", "Indexing", "other-ix", "Indexing", "skipped", "CEF");

        final Map<String, String> found = PromotionLayout.siblingRoots(pipelines, Set.of("ALPHA", "Beta", "Gamma"),
                roles::get, pipeline -> pipeline.uuid().equals("skipped"));

        assertThat(found).isEqualTo(Map.of("Events", "System/Parent", "Indexing", "System/Elastic Indices"));
        assertThat(PromotionLayout.siblingRoots(pipelines, Set.of(), roles::get, pipeline -> false)).isEmpty();
    }

    @Test
    void aFolderIsTakenApartIntoItsFeedRoleAndVersion() {
        assertThat(PromotionLayout.predecessor("/Feeds/Acme/Indexing/V1.0"))
                .isEqualTo(new PromotionLayout.Predecessor("System/Feeds/Acme", "Indexing", "V1.0"));
        assertThat(PromotionLayout.predecessor("System/Feeds/Acme/Common/V1.1"))
                .isEqualTo(new PromotionLayout.Predecessor("System/Feeds/Acme", "Common", "V1.1"));
        assertThat(PromotionLayout.predecessor("System/Feeds/Acme"))
                .isEqualTo(new PromotionLayout.Predecessor("System/Feeds/Acme", null, null));
    }

    @Test
    void theLayoutIsATreeOfTheDestinationFoldersWithNewOnesMarked() {
        world.add(PipelineDoc.TYPE, "ACME-V1.0-Events", world.folder("System/Feeds/Events/Acme").uuid, "prod");
        final List<PromotionLayout.Step> plan = List.of(
                step(PipelineDoc.TYPE, "ACME-V1.1-Events", PromotionLayout.MOVE, "System/Feeds/Events/Acme/V1.1"),
                step(XsltDoc.TYPE, "ACME-V1.1-Events", PromotionLayout.MOVE, "System/Feeds/Events/Acme/V1.1"),
                step(PipelineDoc.TYPE, "ACME-V1.0-Events", PromotionLayout.WRITE_BACK, "prod"),
                step(FeedDoc.TYPE, "ACME-MCP-TEST", PromotionLayout.KEEP, "left in the workspace"));

        final String tree = layout.promotionTree(plan, List.of("System/Feeds/Events/Acme/V1.1"));

        assertThat(tree).isEqualTo("System/Feeds/Events/Acme/\n"
                                   + "├── V1.1/  (new)\n"
                                   + "│   ├── ACME-V1.1-Events  (Pipeline)\n"
                                   + "│   └── ACME-V1.1-Events  (XSLT)\n"
                                   + "└── ACME-V1.0-Events  (Pipeline: updated from its working copy, backed up "
                                   + "first)\n\n"
                                   + "Left in the workspace: ACME-MCP-TEST (Feed)");
    }

    @Test
    void aWorkingCopyWhoseOriginalIsGoneIsSaidApart() {
        final String tree = layout.promotionTree(List.of(
                step(PipelineDoc.TYPE, "ACME-Events", PromotionLayout.MOVE, "System/Feeds/Acme"),
                step(XsltDoc.TYPE, "ACME-Old", PromotionLayout.WRITE_BACK, "gone")), List.of());

        assertThat(tree).isEqualTo("System/Feeds/Acme/\n└── ACME-Events  (Pipeline)\n\n"
                                   + "Updated from working copies: ACME-Old (XSLT)");
    }

    @Test
    void aTranslationIsFiledUnderWhatItWrites() {
        // Asked for by the user: Events, or Records for a pipeline writing Records streams. Live:
        // STROOM_PROXY-RECEIVE-EVENTS (its own stream type Raw Events) is made Records by 'Stroom Proxy Receive
        // Events', read as the records stage: so the feed's stream type can't say, and the pipeline's stage does.
        assertThat(WriteGuard.STAGE_ROLES).containsEntry("translation", "Events").containsEntry("records",
                "Records");
    }

    @Test
    void aVersionIsReadFromTheNameAsAWholeWord() {
        assertThat(PromotionLayout.versions("DELINEA-AUDIT-V1.1-Events")).containsExactly("V1.1");
        assertThat(PromotionLayout.versions("acme-v2-indexing-v2.0.1")).containsExactly("v2", "v2.0.1");
        assertThat(PromotionLayout.versions("DEV1-Events")).isEmpty();
    }

    private static String next(final String predecessor, final String feedFolder) {
        return PromotionLayout.nextVersionPath(PromotionLayout.predecessor(predecessor), feedFolder, "Events",
                "V1.1");
    }

    private static String root(final String predecessor, final String feedFolder, final String group) {
        return PromotionLayout.rootOf(PromotionLayout.predecessor(predecessor), feedFolder, group);
    }

    private static PipelineIndex.Entry entry(final String uuid, final String path) {
        return new PipelineIndex.Entry(uuid, uuid, path, null, null);
    }

    private static PromotionLayout.Step step(final String type,
                                             final String name,
                                             final String action,
                                             final String target) {
        return new PromotionLayout.Step(FakeBuildFacts.doc(type, name + "-" + type, name), action, target, null);
    }
}
