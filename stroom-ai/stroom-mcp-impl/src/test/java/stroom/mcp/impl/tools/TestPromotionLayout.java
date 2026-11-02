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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Promotion's layout (version folders, the documents that join a version, and the tree shown), from testing in
/// production: where an amended pipeline's documents would go wasn't plain (a list in a form), and a new version went
/// beside the old one with nothing to tell them apart (asked for by the user).
class TestPromotionLayout {

    private final BuildWorld world = new BuildWorld();
    private final PromotionLayout layout = new PromotionLayout(new DocumentPlaces(world.documentAccess,
            Mockito.mock(PipelineIndex.class)));

    @Test
    void aNewVersionGoesInAVersionSubfolderBesideItsPredecessor() {
        world.add(PipelineDoc.TYPE, "DELINEA-AUDIT-V1.0-Events", world.folder("System/Feeds/Events/Delinea").uuid,
                "old");
        world.add(PipelineDoc.TYPE, "ACME-V2-Events", world.folder("System/Feeds/Events/Acme/V2").uuid, "older");
        final List<Map<String, Object>> docs = List.of(
                FakeBuildFacts.doc(PipelineDoc.TYPE, "new", "DELINEA-AUDIT-V1.1-Events", "version_of", "old"),
                FakeBuildFacts.doc(PipelineDoc.TYPE, "v3", "ACME-V3-Events", "version_of", "older"),
                FakeBuildFacts.doc(PipelineDoc.TYPE, "given", "X-V9-Events", "version_of", "old"),
                FakeBuildFacts.doc(PipelineDoc.TYPE, "plain", "Brand-New-Events"));

        final PromotionLayout.VersionFolders found = layout.versionFolders(docs, Map.of("given",
                "System/Elsewhere"));

        // Beside V2's folder, not inside it.
        assertThat(found.proposed()).isEqualTo(Map.of("new", "System/Feeds/Events/Delinea/V1.1",
                "v3", "System/Feeds/Events/Acme/V3"));
        assertThat(found.beside()).isEqualTo(Map.of("System/Feeds/Events/Delinea",
                Set.of("System/Feeds/Events/Delinea/V1.1"), "System/Feeds/Events/Acme/V2",
                Set.of("System/Feeds/Events/Acme/V3")));
    }

    @Test
    void aVersionWhoseTypeIsGivenAFolderElsewhereGoesThere() {
        // Seen: TWITTER's V1.1 pipeline left beside its predecessor under /Feeds/Twitter, while every type was
        // given System/MCP Testing/Twitter and its XSLT and documentation went there.
        world.add(PipelineDoc.TYPE, "Twitter-Events", world.folder("System/Feeds/Twitter").uuid, "old");
        final List<Map<String, Object>> docs = List.of(
                FakeBuildFacts.doc(PipelineDoc.TYPE, "new", "Twitter-Events-V1.1", "version_of", "old"));

        assertThat(layout.versionFolders(docs, Map.of(PipelineDoc.TYPE, "System/MCP Testing/Twitter")).proposed())
                .isEmpty();
        // Given the predecessor's folder (without System, as an agent gives it), or none: beside it, as before.
        assertThat(layout.versionFolders(docs, Map.of(PipelineDoc.TYPE, "/Feeds/Twitter")).proposed())
                .isEqualTo(Map.of("new", "System/Feeds/Twitter/V1.1"));
        assertThat(layout.versionFolders(docs, Map.of()).proposed())
                .isEqualTo(Map.of("new", "System/Feeds/Twitter/V1.1"));
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
    void aVersionsOtherNewDocumentsJoinItsFolder() {
        // Seen in e2e: the new indexing pipeline went in v2/, its index, documentation and dashboard beside v1.
        assertThat(PromotionLayout.joining(Map.of("System/Prod/door", Set.of("System/Prod/door/v2"))))
                .isEqualTo(Map.of("System/Prod/door", "System/Prod/door/v2"));
        // A predecessor already in a version folder: the new one beside it, and documents meant for either place
        // join it.
        assertThat(PromotionLayout.joining(Map.of("System/Prod/door/v2", Set.of("System/Prod/door/v3"))))
                .isEqualTo(Map.of("System/Prod/door/v2", "System/Prod/door/v3", "System/Prod/door",
                        "System/Prod/door/v3"));
        // The predecessor in a role subfolder: documents given the source's folder join the version too.
        assertThat(PromotionLayout.joining(Map.of("System/Prod/door/Indexing",
                Set.of("System/Prod/door/Indexing/v2")))).isEqualTo(Map.of("System/Prod/door/Indexing",
                "System/Prod/door/Indexing/v2", "System/Prod/door", "System/Prod/door/Indexing/v2"));
        // Two versions meant for one place: neither is guessed.
        assertThat(PromotionLayout.joining(Map.of("System/A", Set.of("System/A/v2", "System/A/v3")))).isEmpty();
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

    private static PromotionLayout.Step step(final String type,
                                             final String name,
                                             final String action,
                                             final String target) {
        return new PromotionLayout.Step(FakeBuildFacts.doc(type, name + "-" + type, name), action, target, null);
    }
}
