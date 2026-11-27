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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// What of a build promotion leaves in it because nothing uses it (decided by the user, 2026-11-18: seen in a user
/// test, an older Data Splitter the build had replaced was promoted with the rest).
class TestPromotionUses {

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final Map<String, Object> stored = new HashMap<>();
    private final List<Map<String, Object>> docs = new ArrayList<>();
    private final PromotionUses uses = new PromotionUses(documentAccess);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(documentAccess.read(any(DocRef.class), any(Class.class))).thenAnswer(inv -> {
            final DocRef ref = inv.getArgument(0);
            final Class<?> type = inv.getArgument(1);
            return Optional.ofNullable(stored.get(ref.getUuid())).filter(type::isInstance);
        });
    }

    @Test
    void whatThePipelinesReachIsUsedAndTheRestIsNot() {
        listed(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events");
        listed(TextConverterDoc.TYPE, "ds", "ACME-V1.0-Splitter");
        listed(TextConverterDoc.TYPE, "old", "ACME-V1.0-Splitter-OLD");
        listed(XsltDoc.TYPE, "x", "ACME-V1.0-Events");
        listed(XsltDoc.TYPE, "h", "ACME Helpers");
        listed(XsltDoc.TYPE, "dead", "ACME Dead");
        listed(XsltDoc.TYPE, "dead-import", "ACME Dead Import");
        // An index is always promoted (a registry names it).
        listed(LuceneIndexDoc.TYPE, "ix", "ACME Index");
        listed(FeedDoc.TYPE, "f", "ACME");
        listed(DocumentationDoc.TYPE, "doc-ev", "ACME-V1.0-Events");
        listed(DocumentationDoc.TYPE, "doc-old", "ACME-V1.0-Splitter-OLD" + WriteDocumentationTool.EVALUATION);
        pipeline("ev", null, Map.of("textConverter", new DocRef(TextConverterDoc.TYPE, "ds"), "xslt",
                new DocRef(XsltDoc.TYPE, "x")));
        xslt("x", "<xsl:include href=\"ACME Helpers\"/>");
        xslt("h", "");
        // Imported only by an XSLT nothing uses: not used either.
        xslt("dead", "<xsl:import href=\"dead-import\"/>");
        xslt("dead-import", "");

        assertThat(uses.unused(docs, Set.of()))
                .containsExactly("old", "dead", "dead-import", "doc-old");
    }

    @Test
    void anIndexTheAgentMadeToTestWithThatNothingPromotedWritesToStaysWithItsDashboard() {
        // Seen in an eval (case 61): the agent made test indexes of its own (ACME-INDEX-TEST, ACME-INDEX-TEST2),
        // pointed the new version's indexing pipeline at each in turn and back at the production index, and the
        // promotion moved them and their verification dashboards to production beside the pipeline.
        listed(PipelineDoc.TYPE, "ip", "ACME - Indexing V1.1");
        listed(LuceneIndexDoc.TYPE, "t1", "ACME-INDEX-TEST");
        listed(LuceneIndexDoc.TYPE, "t2", "ACME-INDEX-TEST2");
        listed(DashboardDoc.TYPE, "d1", "ACME-INDEX-TEST" + PromotionTidyUp.VERIFY);
        listed(DashboardDoc.TYPE, "d2", "ACME-INDEX-TEST2" + PromotionTidyUp.VERIFY);
        // One the pipeline writes to, one an index plan names, and one the feed's registry names: promoted.
        listed(LuceneIndexDoc.TYPE, "written", "ACME-OTHER-TEST");
        listed(LuceneIndexDoc.TYPE, "planned", "acme-test");
        listed(LuceneIndexDoc.TYPE, "registered", "ACME-REG-TEST");
        listed(DashboardDoc.TYPE, "d3", "ACME-OTHER-TEST" + PromotionTidyUp.VERIFY);
        // An index named for nothing to test with is promoted as before, whatever writes to it.
        listed(LuceneIndexDoc.TYPE, "plain", "ACME Index");
        listed(XsltDoc.TYPE, "ix-x", "acme-index-XSLT");
        listed(DocumentationDoc.TYPE, "reg", "ACME" + SourceTypeRegistry.SUFFIX);
        pipeline("ip", null, Map.of("index", new DocRef(LuceneIndexDoc.TYPE, "written"), "xslt",
                new DocRef(XsltDoc.TYPE, "ix-x")));
        stored.put("ix-x", XsltDoc.builder().uuid("ix-x").name("acme-index-XSLT").data("")
                .description(KeptMapping.withMapping("", "index", Map.of("index_name", "ACME-TEST"))).build());
        stored.put("reg", DocumentationDoc.builder().uuid("reg").name("ACME" + SourceTypeRegistry.SUFFIX)
                .data("{\"index_doc\": {\"uuid\": \"registered\"}}").build());
        verifyDashboard("d1", "t1");
        verifyDashboard("d2", "t2");
        verifyDashboard("d3", "written");

        assertThat(uses.unused(docs, Set.of())).containsExactlyInAnyOrder("t1", "t2", "d1", "d2");
        assertThat(PromotionUses.testMarked("ACME-INDEX-MCP-TEST")).isTrue();
        assertThat(PromotionUses.testMarked("ACME-CONTEST")).isFalse();
        assertThat(PromotionUses.testMarked("ACME-TESTING")).isFalse();
    }

    @Test
    void aParentPipelineInTheBuildAndAReferenceLoadersPipelineAreWalkedToo() {
        listed(PipelineDoc.TYPE, "child", "ACME-V1.0-Events");
        listed(PipelineDoc.TYPE, "parent", "ACME template");
        listed(PipelineDoc.TYPE, "loader", "ACME-V1.0-Reference");
        listed(XsltDoc.TYPE, "x", "ACME-V1.0-Events");
        listed(XsltDoc.TYPE, "rx", "ACME-V1.0-Reference");
        listed(DictionaryDoc.TYPE, "d1", "ACME Words");
        listed(DictionaryDoc.TYPE, "d2", "ACME Lists");
        stored.put("child", PipelineDoc.builder().uuid("child").name("ACME-V1.0-Events")
                .parentPipeline(new DocRef(PipelineDoc.TYPE, "prod-template"))
                .pipelineData(new PipelineDataBuilder()
                        .addPipelineReference(new PipelineReference(new DocRef(PipelineDoc.TYPE, "loader"),
                                new DocRef(FeedDoc.TYPE, "ref-feed"), "Reference"))
                        .build())
                .build());
        // A production template the build's pipeline inherits from, inheriting in turn from the build's.
        pipeline("prod-template", new DocRef(PipelineDoc.TYPE, "parent"), Map.of());
        pipeline("parent", null, Map.of("xslt", new DocRef(XsltDoc.TYPE, "x")));
        pipeline("loader", null, Map.of("xslt", new DocRef(XsltDoc.TYPE, "rx")));
        xslt("x", "<xsl:variable select=\"stroom:dictionary('ACME Words')\"/>");
        xslt("rx", "");
        stored.put("d1", DictionaryDoc.builder().uuid("d1").name("ACME Words")
                .imports(List.of(new DocRef(DictionaryDoc.TYPE, "d2"))).build());
        stored.put("d2", DictionaryDoc.builder().uuid("d2").name("ACME Lists").build());

        assertThat(uses.unused(docs, Set.of())).isEmpty();
    }

    @Test
    void nothingIsLeftOutWhereTheWalkCantBeSure() {
        listed(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events");
        listed(TextConverterDoc.TYPE, "old", "ACME-V1.0-Splitter-OLD");

        // The build's pipeline can't be read.
        assertThat(uses.unused(docs, Set.of())).isEmpty();

        // It picks its XSLT by a name pattern.
        stored.put("ev", PipelineDoc.builder().uuid("ev").name("ACME-V1.0-Events")
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(new PipelineProperty("translationFilter", "xsltNamePattern",
                                new PipelinePropertyValue("${feed}")))
                        .build())
                .build());
        assertThat(uses.unused(docs, Set.of())).isEmpty();
    }

    @Test
    void anIndexingXsltHoldingItsPlanIsUsedWithNoPipelineReadingItYet() {
        // Seen in e2e: an indexing XSLT saved from its plan before any indexing pipeline, its index decided through
        // the feed's registry.
        listed(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events");
        listed(XsltDoc.TYPE, "ix-x", "acme-index-XSLT");
        listed(XsltDoc.TYPE, "ix-helpers", "acme-index-helpers");
        pipeline("ev", null, Map.of());
        stored.put("ix-x", XsltDoc.builder().uuid("ix-x").name("acme-index-XSLT").data("<xsl:import "
                        + "href=\"acme-index-helpers\"/>")
                .description(KeptMapping.withMapping("", "index", Map.of("index_name", "acme"))).build());
        xslt("ix-helpers", "");

        assertThat(uses.unused(docs, Set.of())).isEmpty();
    }

    @Test
    void aBuildOfNoPipelineLeavesNothingOut() {
        listed(TextConverterDoc.TYPE, "ds", "ACME-V1.0-Splitter");
        listed(XsltDoc.TYPE, "x", "ACME-V1.0-Events");
        docs.add(FakeBuildFacts.doc(PipelineDoc.TYPE, "copy", "ACME-V1.0-Events", "working_copy_of", "prod"));

        assertThat(uses.unused(docs, Set.of())).isEmpty();
    }

    @Test
    void workingCopiesModulesAndDocumentationSharingAUsedNameAreNeverLeftOut() {
        listed(PipelineDoc.TYPE, "ev", "ACME-V1.0-Events");
        listed(XsltDoc.TYPE, "old-x", "ACME-V1.0-Events");
        listed(DocumentationDoc.TYPE, "doc-ev", "ACME-V1.0-Events");
        listed(XsltDoc.TYPE, "module", "ACME Common");
        docs.add(FakeBuildFacts.doc(XsltDoc.TYPE, "copy", "ACME Shared", "working_copy_of", "prod"));
        pipeline("ev", null, Map.of());
        xslt("module", "");
        xslt("copy", "");

        // The documentation is the pipeline's, though an XSLT of its name is unused.
        assertThat(uses.unused(docs, Set.of("module"))).containsExactly("old-x");
    }

    private void listed(final String type, final String uuid, final String name) {
        docs.add(FakeBuildFacts.doc(type, uuid, name));
    }

    private void pipeline(final String uuid, final DocRef parent, final Map<String, DocRef> properties) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        properties.forEach((name, ref) -> data.addProperty(new PipelineProperty("element", name,
                new PipelinePropertyValue(ref))));
        stored.put(uuid, PipelineDoc.builder().uuid(uuid).name(uuid).parentPipeline(parent)
                .pipelineData(data.build()).build());
    }

    private void verifyDashboard(final String uuid, final String indexUuid) {
        stored.put(uuid, DashboardDoc.builder().uuid(uuid).name(uuid).dashboardConfig(DashboardConfig.builder()
                .components(List.of(ComponentConfig.builder().id("query").type("query")
                        .settings(QueryComponentSettings.builder()
                                .dataSource(new DocRef(LuceneIndexDoc.TYPE, indexUuid)).build())
                        .build()))
                .build()).build());
    }

    private void xslt(final String uuid, final String code) {
        stored.put(uuid, XsltDoc.builder().uuid(uuid).name(uuid).data(code).build());
    }
}
