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

import stroom.ai.impl.tools.JsonValues;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// The build record, and the clean steps, verifications and validations kept in it: in a fixed format, so a record
/// already in Stroom (`stored-build-record.md` is one) still reads, and is written back as it was.
class TestBuildRecords {

    private static final String BUILD = "acme-v1";
    private static final DocRef P = new DocRef(PipelineDoc.TYPE, "p", "Acme");

    private final BuildWorld world = new BuildWorld();
    private final BuildRecords records = world.records();

    @BeforeEach
    void setUp() {
        // The template sets the text converter; the pipeline sets its own XSLT.
        PipelineLayers.given(world.pipelineService, "p", PipelineLayers.of(
                PipelineLayers.layer("t")
                        .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")
                        .property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "tc", "shared")),
                PipelineLayers.layer("p")
                        .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x", "Acme"))));
        code("<xsl:stylesheet>v1</xsl:stylesheet>");
        world.docs.put("tc", TextConverterDoc.builder().uuid("tc").name("shared").data("template code").build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "Acme", "p");
        world.inBuild(BUILD, PipelineDoc.TYPE, "Other", "q");
    }

    @Test
    void onlyThePipelinesOwnCodeIsFingerprinted() {
        final Map<String, String> prints = records.codeFingerprint("p", null);
        assertThat(prints).containsOnlyKeys("translationFilter");
        // The digest is fixed: were it to change, every clean step already recorded would no longer match.
        assertThat(prints.get("translationFilter"))
                .isEqualTo("61b12115efa32e92a6176df5b0526c120b8df483378e659a67ac1bec0f97152b");
        assertThat(BuildRecords.fingerprintDigest(prints)).isEqualTo("2ab6098c06a45eba");
    }

    @Test
    void theVersionHistoryCommentIsNotCode() {
        code("<!-- stroom-mcp version history\n| v1 | 2026-10-10 |\nend of version history -->\n"
             + "<xsl:stylesheet>v1</xsl:stylesheet>");
        assertThat(BuildRecords.fingerprintDigest(records.codeFingerprint("p", null))).isEqualTo("2ab6098c06a45eba");
    }

    @Test
    void aCleanDraftCountsOnceItIsSavedAndALaterEditDoesNot() {
        assertThat(records.steppedClean(P)).isFalse();
        final Map<String, String> draft = Map.of("translationFilter", "<xsl:stylesheet>v2</xsl:stylesheet>");
        assertThat(records.recordPassed(P, BuildRecords.STEPPED, draft, null)).contains("recorded");
        // The saved code is still v1.
        assertThat(records.steppedClean(P)).isFalse();
        code(draft.get("translationFilter"));
        assertThat(records.steppedClean(P)).isTrue();
        // Another node, or a restart: a fresh reader reads the same record.
        assertThat(new BuildWorldReader(world).records().steppedClean(P)).isTrue();
        code("<xsl:stylesheet>v3</xsl:stylesheet>");
        assertThat(records.steppedClean(P)).isFalse();
    }

    @Test
    void recordsAreKeptInTheBuildRecordNotAsTags() {
        // Asked for by the user: tags are what people filter the explorer by, and per-build and per-digest tags grew
        // the list with every build. Only mcp-managed and mcp-generated are on the build's documents.
        records.recordPassed(P, BuildRecords.STEPPED, null, null);
        records.recordPassed(P, BuildRecords.VERIFIED, null, null);
        records.recordPassed(P, BuildRecords.VALIDATED, null, null);
        assertThat(world.tags("p")).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
        final Map<String, Object> entry = records.entry(BUILD, "p");
        assertThat(entry.get("stepped")).isEqualTo(entry.get("verified")).isEqualTo(entry.get("validated"));
        assertThat(entry.get("name")).isEqualTo("Acme");
        final String text = world.body(recordUuid());
        assertThat(text).contains("don't edit it. Don't move or rename it").contains("| Acme | Pipeline |");
        // Not one of the build's documents: build_status and promotion don't list it.
        assertThat(world.guard().folderContents(BUILD)).extracting(WriteGuard.BuildDoc::name)
                .doesNotContain(BuildRecords.NAME);
    }

    @Test
    void pipelinesTheServerDoesNotManageAreNeverRecorded() {
        final DocRef prod = world.add(PipelineDoc.TYPE, "Prod", world.folder("System/Feeds").uuid, "prod")
                .docRef();
        assertThat(records.recordPassed(prod, BuildRecords.VERIFIED, null, null)).isEmpty();
        assertThat(world.docs.values()).noneMatch(DocumentationDoc.class::isInstance);
        assertThat(world.tags("prod")).isEmpty();
    }

    @Test
    void onlyTheRecordsThatCanStillMatterAreKept() {
        // The new record, and the one for the code saved now: a draft stepped clean doesn't drop the saved code's.
        records.recordPassed(P, BuildRecords.STEPPED, null, Map.of("stepped_streams", List.of(7)));
        for (int n = 0; n < 8; n++) {
            records.recordPassed(P, BuildRecords.STEPPED, Map.of("translationFilter", "draft " + n), null);
        }
        assertThat(JsonValues.strings(records.entry(BUILD, "p").get("stepped"))).hasSize(2);
        assertThat(records.steppedClean(P)).isTrue();
        assertThat(records.entry(BUILD, "p").get("stepped_streams")).isEqualTo(List.of(7));
        // The last draft saved: recorded, as stepped before it was saved.
        code("draft 7");
        assertThat(records.steppedClean(P)).isTrue();
    }

    @Test
    void tagsOfEarlierVersionsAreFoldedIntoTheRecordAndRemoved() {
        // Seen in production: a build in progress when the server was upgraded, its records still tags.
        final String digest = BuildRecords.fingerprintDigest(records.codeFingerprint("p", null));
        world.tags("p").addAll(List.of("mcp-build-" + BUILD, "mcp-stepped-20261010005129-" + digest,
                "mcp-validated-" + digest));
        world.tags("q").addAll(List.of("mcp-build-" + BUILD, "mcp-copy-of-1a2b-3c4d"));
        assertThat(records.steppedClean(P)).isTrue();
        assertThat(records.validated(P)).isTrue();
        assertThat(records.verified(P)).isFalse();
        assertThat(world.tags("p")).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(world.tags("q")).containsExactlyInAnyOrder(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(records.entry(BUILD, "q").get("copy_of")).isEqualTo("1a2b-3c4d");
        assertThat(world.guard().copyOf(new DocRef(PipelineDoc.TYPE, "q", "Other"))).contains("1a2b-3c4d");
    }

    @Test
    void aRecordSavedByAnotherNodeMeanwhileIsKept() {
        // Two nodes recording at once: Stroom refuses the save of a doc changed since it was read, and the change is
        // made again on the newer one.
        records.update(BUILD, P, entry -> entry.put("stepped", List.of("a")));
        final boolean[] raced = {false};
        world.beforeSave = () -> {
            if (!raced[0]) {
                raced[0] = true;
                final String uuid = recordUuid();
                final DocumentationDoc current = (DocumentationDoc) world.docs.get(uuid);
                final Map<String, Object> state = BuildRecords.parse(current.getData());
                final Map<String, Object> other = new LinkedHashMap<>();
                other.put("name", "Other");
                other.put("verified", List.of("b"));
                BuildRecords.docs(state).put("q", other);
                // The other node's save lands first.
                world.docs.put(uuid, current.copy().data(BuildRecords.render(BUILD, state)).version("other")
                        .build());
            }
        };
        records.update(BUILD, P, entry -> entry.put("validated", List.of("c")));
        final Map<String, Object> state = records.load(BUILD);
        assertThat(((Map<?, ?>) BuildRecords.docs(state).get("q")).get("verified")).isEqualTo(List.of("b"));
        assertThat(((Map<?, ?>) BuildRecords.docs(state).get("p")).get("validated")).isEqualTo(List.of("c"));
    }

    @Test
    void theRecordGoesOnceNothingItKeptIsLeftInTheBuild() {
        records.recordPassed(P, BuildRecords.STEPPED, null, null);
        records.update(BUILD, new DocRef(PipelineDoc.TYPE, "q", "Other"), entry -> entry.put("stepped",
                List.of("x")));
        // q kept in the workspace.
        records.forget(BUILD, List.of("p"));
        assertThat(BuildRecords.docs(records.load(BUILD))).containsOnlyKeys("q");
        records.forget(BUILD, List.of("q"));
        assertThat(world.guard().folderContents(BUILD, true)).extracting(WriteGuard.BuildDoc::name)
                .doesNotContain(BuildRecords.NAME);
    }

    @Test
    void aManagedPipelineOutsideABuildFolderSaysItsStepWasNotRecorded() {
        world.move("p", world.folder("System/Feeds").uuid);
        final Optional<String> outcome = records.recordPassed(P, BuildRecords.STEPPED, null, null);
        assertThat(outcome).hasValueSatisfying(said -> assertThat(said)
                .contains("not in a build folder")
                .contains("promotion will report it as not done"));
    }

    @Test
    void existingFeedsTheBuildWorksFromAreRecordedOnce() {
        records.addSourceFeeds(BUILD, List.of("ACME"));
        records.addSourceFeeds(BUILD, List.of("ACME", "ACME-2"));
        assertThat(records.load(BUILD).get(BuildRecords.SOURCE_FEEDS)).isEqualTo(List.of("ACME", "ACME-2"));
        assertThat(world.body(recordUuid())).contains("Works from existing feeds: ACME, ACME-2.");
    }

    @Test
    void aRecordIsWrittenInTheStoredForm() throws IOException {
        final String python = resource("stored-build-record.md");
        final Map<String, Object> state = BuildRecords.parse(python);
        assertThat(BuildRecords.render(BUILD, state)).isEqualTo(python);
        // And reads back to the same state.
        assertThat(BuildRecords.parse(BuildRecords.render(BUILD, state))).isEqualTo(state);
    }

    @Test
    void aRecordInTheStoredFormatIsRead() throws IOException {
        final DocRef record = world.inBuild(BUILD, DocumentationDoc.TYPE, BuildRecords.NAME, "r");
        world.docs.put("r", ((DocumentationDoc) world.docs.get("r")).copy().data(resource("stored-build-record.md"))
                .build());
        assertThat(records.entry(BUILD, "p").get("copy_of")).isEqualTo("prod-1");
        assertThat(world.guard().copyOf(P)).contains("prod-1");
        assertThat(record.getName()).isEqualTo(BuildRecords.NAME);
    }

    @Test
    void anUnreadableRecordIsAnEmptyOne() {
        assertThat(BuildRecords.parse(null)).isEqualTo(BuildRecords.empty());
        assertThat(BuildRecords.parse("```json build-record\nnot json\n```")).isEqualTo(BuildRecords.empty());
        assertThat(BuildRecords.parse("```json build-record\n{\"docs\": []}\n```")).isEqualTo(BuildRecords.empty());
    }

    private void code(final String xslt) {
        world.docs.put("x", XsltDoc.builder().uuid("x").name("Acme").data(xslt).build());
    }

    private String recordUuid() {
        return world.guard().folderContents(BUILD, true).stream()
                .filter(doc -> BuildRecords.NAME.equals(doc.name()))
                .findFirst()
                .orElseThrow()
                .uuid();
    }

    private static String resource(final String name) throws IOException {
        try (final InputStream in = TestBuildRecords.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // A second reader of the same explorer, as another node is.
    private record BuildWorldReader(BuildWorld world) {

        BuildRecords records() {
            return new BuildRecords(world::guard, world.documentAccess, new DocumentWrites(world.documentAccess),
                    () -> world.explorerService, world.pipelineShapes);
        }
    }
}
