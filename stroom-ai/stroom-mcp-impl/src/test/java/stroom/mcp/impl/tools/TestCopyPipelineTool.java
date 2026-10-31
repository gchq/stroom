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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// copy_pipeline (the Stroom MCP server's `tools/pipeline_writes.py` `copy_pipeline`, and its tests in
/// `tests/test_slots.py` and `tests/test_choices_and_unknown.py`).
class TestCopyPipelineTool {

    private static final String BUILD = PipelineWorld.BUILD;
    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, PipelineWorld.TEMPLATE_UUID,
            "Event Data (Text)");

    private final PipelineWorld pipelines = new PipelineWorld();
    private final CopyPipelineTool tool = pipelines.copyTool();

    @Test
    void copyingATemplateIsRefusedInFavourOfInheriting() {
        pipelines.template(TEMPLATE.getUuid(), TEMPLATE.getName(), PipelineLayers.layer("t"));
        Mockito.when(pipelines.templates.templateReason(TEMPLATE.getUuid()))
                .thenReturn("it is one of the template pipelines");

        final ToolResult refused = call("{\"build\": \"acme-v2\", \"source_uuid\": \"" + TEMPLATE.getUuid()
                                        + "\", \"new_name\": \"Copy of template\"}", PortedToolCalls.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("'Event Data (Text)' is a template (it is one of the template "
                                              + "pipelines): templates are inherited, not copied.")
                .contains("create_pipeline(name=..., template_uuid='" + TEMPLATE.getUuid() + "')");
        assertThat(pipelines.world.created).isEmpty();
    }

    @Test
    void aPipelineCopyLeavesOutTheDocumentsSetPropertiesReplaces() {
        // Seen in e2e: v2's XSLT was made first, then v1's was copied and renamed to the same name, which the build
        // refused.
        source();

        final PortedToolCalls.FormUser user = PortedToolCalls.declining();
        call("{\"build\": \"acme-v2\", \"source_uuid\": "
                                                                  + "\"p1\", \"new_name\": \"ACME-V2 - Indexing\", "
                                                                  + "\"rename\": {\"V1\": \"V2\"}, "
                                                                  + "\"set_properties\": [{\"element\": "
                                                                  + "\"xsltFilter\", \"name\": \"xslt\", "
                                                                  + "\"doc_uuid\": \"x2\", \"doc_type\": "
                                                                  + "\"XSLT\"}]}", user.context());

        assertThat(user.shown()).singleElement().asString().containsPattern("- copied documents: ACME-V2(\n|$)");
    }

    @Test
    void aNewVersionIsTheOriginalWithItsOwnDocumentsCopiedAndRewired() {
        source();
        final ToolCallContext context = PortedToolCalls.answering(Map.of("value", true));

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"" + BUILD + "\", \"source_uuid\": "
                                                                 + "\"p1\", \"new_name\": \"ACME-V2 - Indexing\", "
                                                                 + "\"rename\": {\"V1\": \"V2\"}, "
                                                                 + "\"set_properties\": \"splitFilter.splitCount=5\"}",
                context));

        final PipelineDoc copy = pipelines.pipelineNamed("ACME-V2 - Indexing");
        final PipelineDoc source = pipelines.pipelineDoc("p1");
        assertThat(copy.getParentPipeline()).isEqualTo(TEMPLATE);
        assertThat(copy.getDescription()).isEqualTo("Indexes Acme.");
        // The same element changes as the original's.
        assertThat(copy.getPipelineData().getAddedElements()).isEqualTo(source.getPipelineData()
                .getAddedElements());
        final XsltDoc xslt = (XsltDoc) docNamed("ACME-V2-XSLT");
        final TextConverterDoc converter = (TextConverterDoc) docNamed("ACME-V2");
        assertThat(xslt.getData()).isEqualTo("<xsl:stylesheet/>");
        assertThat(converter.getData()).isEqualTo("<dataSplitter/>");
        assertThat(PipelineDocs.addedProperties(copy)).containsExactly(
                new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE,
                        xslt.getUuid(), "ACME-V2-XSLT"))),
                new PipelineProperty("dsParser", "textConverter", new PipelinePropertyValue(new DocRef(
                        TextConverterDoc.TYPE, converter.getUuid(), "ACME-V2"))),
                new PipelineProperty("splitFilter", "splitCount", new PipelinePropertyValue(5)));
        // A new version, promoted alongside the original.
        assertThat(pipelines.world.records().entry(BUILD, copy.getUuid())).containsEntry("version_of", "p1");
        assertThat(pipelines.world.records().entry(BUILD, xslt.getUuid())).containsEntry("version_of", "x1");
        assertThat(made).containsEntry("copy_of", "ACME-V1 - Indexing").containsEntry("working_copy", false)
                .containsKey("next");
        assertThat(made.get("copied_documents")).isEqualTo(List.of(
                Map.of("type", "XSLT", "uuid", xslt.getUuid(), "name", "ACME-V2-XSLT"),
                Map.of("type", "TextConverter", "uuid", converter.getUuid(), "name", "ACME-V2")));
    }

    @Test
    void aWorkingCopyRecordsWhatItIsACopyOf() {
        source();

        call("{\"build\": \"" + BUILD + "\", \"source_uuid\": \"p1\", \"new_name\": \"ACME-V1 - Indexing\", "
             + "\"working_copy\": true}", PortedToolCalls.answering(Map.of("value", true)));

        final PipelineDoc copy = pipelines.world.docs.values().stream()
                .filter(doc -> doc instanceof final PipelineDoc pipeline && !"p1".equals(pipeline.getUuid())
                               && "ACME-V1 - Indexing".equals(pipeline.getName()))
                .map(PipelineDoc.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(pipelines.world.records().entry(BUILD, copy.getUuid())).containsEntry("copy_of", "p1");
        // Its XSLT's copy records the version it was copied at, so promotion can tell if production has changed
        // since (found reviewing the tools: a production edit made after the copy was lost on promotion).
        final XsltDoc xsltCopy = pipelines.world.docs.values().stream()
                .filter(doc -> doc instanceof final XsltDoc xslt && !"x1".equals(xslt.getUuid()))
                .map(XsltDoc.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(pipelines.world.records().entry(BUILD, xsltCopy.getUuid()))
                .containsEntry("copy_of", "x1")
                .containsEntry(BuildRecords.COPY_OF_VERSION, "v0");
    }

    private void source() {
        pipelines.template(TEMPLATE.getUuid(), TEMPLATE.getName(), PipelineLayers.layer(TEMPLATE.getUuid())
                .chain("dsParser", "DSParser", "splitFilter", "SplitFilter", "xsltFilter", "XSLTFilter"));
        pipelines.world.add(XsltDoc.TYPE, "ACME-V1-XSLT", pipelines.world.folder("System/Feeds/Acme").uuid, "x1");
        pipelines.world.docs.put("x1", XsltDoc.builder().uuid("x1").name("ACME-V1-XSLT").version("v0")
                .data("<xsl:stylesheet/>").build());
        pipelines.world.add(TextConverterDoc.TYPE, "ACME-V1", pipelines.world.folder("System/Feeds/Acme").uuid, "t1");
        pipelines.world.docs.put("t1", TextConverterDoc.builder().uuid("t1").name("ACME-V1").version("v0")
                .data("<dataSplitter/>").build());
        final PipelineDoc doc = pipelines.productionPipeline("p1", "ACME-V1 - Indexing", TEMPLATE,
                PipelineLayers.layer("p1")
                        .element("extraFilter", "XSLTFilter")
                        .link("xsltFilter", "extraFilter")
                        .property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, "x1", "ACME-V1-XSLT"))
                        .property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "t1", "ACME-V1")));
        pipelines.world.docs.put("p1", doc.copy().description("Indexes Acme.").build());
    }

    private Object docNamed(final String name) {
        return pipelines.world.nodes.values().stream()
                .filter(node -> name.equals(node.name) && pipelines.world.tags(node.uuid).contains(WriteGuard.MANAGED))
                .map(node -> pipelines.world.docs.get(node.uuid))
                .findFirst()
                .orElseThrow();
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return tool.execute(PortedToolCalls.call(CopyPipelineTool.NAME, arguments), context);
    }
}
