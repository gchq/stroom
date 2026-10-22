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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineLayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestPipelineTemplates {

    private static final String SCHEMA_LOCATION = "Attempt to set property \"schemaLocation\" on element "
                                                  + "\"schemaFilter\" but property is unknown.";

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final PipelineService pipelineService = PipelineLayers.service();
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final Map<String, Entry> index = new LinkedHashMap<>();
    private PipelineTemplates templates;

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineIndex.read()).thenReturn(index);
        Mockito.when(processorFilters.all()).thenReturn(List.of());
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(documentAccess.read(any(), eq(XsltDoc.class))).thenReturn(Optional.empty());
        templates = new PipelineTemplates(pipelineIndex, PipelineLayers.shapes(pipelineService, new AiConfig()),
                processorFilters, documentAccess, () -> docFinder);
    }

    @Test
    void testOneChildStroomCannotLoadDoesNotHideTheOthers() {
        // Live: a pipeline storing schemaFilter.schemaLocation (a property the element lacks) made Stroom answer
        // 500, and describe_template failed outright; the agent then tried to "fix" the schema filter.
        add("a", "Bad", "System/X", "t");
        add("b", "Good", "System/X", "t");
        unloadable("a", SCHEMA_LOCATION);
        PipelineLayers.given(pipelineService, "b", PipelineLayers.of(
                PipelineLayers.layer("t").chain("xmlParser", "XMLParser", "xsltFilter", "XSLTFilter"),
                PipelineLayers.layer("b").property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, "x", "X"))));
        final List<Map<String, Object>> children = templates.children("t");
        assertThat(children).hasSize(2);
        assertThat(String.valueOf(children.get(0).get("unreadable"))).contains("schemaLocation");
        assertThat(children.get(1).get("sets")).isEqualTo(List.of("xsltFilter.xslt"));

        // The template itself unreadable: said to be Stroom's stored settings, for an administrator, not to set
        // here.
        unloadable("t", "x");
        assertThatThrownBy(() -> templates.contract("t"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("an administrator fixes the template in the Stroom UI");
    }

    @Test
    void testADanglingXsltIsSkippedAndNamedNotAFailure() {
        // Seen on live in VS Code: the explorer listed an XSLT whose document was deleted, and describe_template
        // failed with Stroom's 500, so the agent never saw the template's children and contract.
        final String own = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                           + "<xsl:import href=\"shared-lib\"/><xsl:template match=\"/\"/></xsl:stylesheet>";
        final String lib = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                           + "<xsl:template name=\"device\"/></xsl:stylesheet>";
        xslt("own", own);
        xslt("live", lib);
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "shared-lib")).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "dead", "shared-lib"), new DocRef(XsltDoc.TYPE, "live", "shared-lib")));
        PipelineLayers.given(pipelineService, "a", PipelineLayers.of(PipelineLayers.layer("a")
                .chain("p", "XMLParser", "x", "XSLTFilter").property("x", "xslt", new DocRef(XsltDoc.TYPE, "gone"))));
        PipelineLayers.given(pipelineService, "b", PipelineLayers.of(PipelineLayers.layer("b")
                .chain("p", "XMLParser", "x", "XSLTFilter").property("x", "xslt", new DocRef(XsltDoc.TYPE, "own"))));

        final List<Map<String, Object>> shared = templates.sharedXsltUsage(List.of(
                Map.of("uuid", "a", "name", "A"), Map.of("uuid", "b", "name", "B")));

        final List<Map<String, Object>> contents = shared.stream()
                .filter(row -> row.containsKey("document_contents"))
                .toList();
        assertThat(contents).hasSize(1);
        assertThat(((Map<?, ?>) contents.getFirst().get("document_contents")).get("uuid")).isEqualTo("live");
        assertThat(shared.stream()
                .filter(row -> row.containsKey("unreadable_xslt"))
                .map(row -> row.get("unreadable_xslt"))
                .sorted()
                .toList())
                .containsExactly("dead", "gone");
    }

    @Test
    void testAPipelineStroomCantBuildIsLeftOutOfTheTemplateSearch() {
        // Seen live: a State Store loader with an element type this Stroom doesn't have failed the whole search.
        add("t", "json-in v3", "System/Acme/Bases", null);
        add("b", "State Store Loader", "System/Standard Pipelines", null);
        PipelineLayers.given(pipelineService, "t", jsonTranslationTemplate("t"));
        unloadable("b", "Element type \"StateFilter\" is unknown");
        final Map<String, Object> found = templates.find("translation");
        assertThat(names(found)).containsExactly("json-in v3");
        assertThat(String.valueOf(((List<?>) found.get("unreadable")).getFirst()))
                .startsWith("System/Standard Pipelines/State Store Loader: ")
                .contains("StateFilter");
    }

    @Test
    void testAPipelineThatLeavesItsXsltForAChildIsATemplateWhereverItIs() {
        add("t", "json-in v3", "System/Acme/Pipeline Bases", null);
        add("p", "ACME-V1-Events", "System/Acme/Sources", null);
        PipelineLayers.given(pipelineService, "t", jsonTranslationTemplate("t"));
        PipelineLayers.given(pipelineService, "p", PipelineLayers.of(PipelineLayers.layer("p")
                .chain("jsonParser", "JSONParser", "translationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x"))
                .property("schemaFilter", "schemaGroup", "EVENTS")));
        final Map<String, Object> found = templates.find("translation");
        // A source's own complete pipeline (its XSLT set) is not a template, though it has no parent either.
        assertThat(candidates(found)).extracting(c -> c.get("name") + "/" + c.get("source"))
                .containsExactly("json-in v3/template_like");
    }

    @Test
    void testTemplatesAreRankedConfiguredFirstThenInheritedThenStandard() {
        add("std", "Indexing", "System/Template Pipelines", null);
        add("ours", "Event Data (JSON)", "System/Template Pipelines", null);
        add("base", "House base", "System/Acme", null);
        add("c1", "Child 1", "System/Acme", "base");
        add("c2", "Child 2", "System/Acme", "base");
        PipelineLayers.given(pipelineService, "ours", jsonTranslationTemplate("ours"));
        PipelineLayers.given(pipelineService, "base", jsonTranslationTemplate("base"));
        PipelineLayers.given(pipelineService, "std", PipelineLayers.of(PipelineLayers.layer("std")
                .chain("xmlParser", "XMLParser", "indexingFilter", "IndexingFilter")));
        final Map<String, Object> found = templates.find("translation");
        assertThat(candidates(found)).extracting(c -> c.get("name") + "/" + c.get("source") + "/" + c.get("children"))
                .containsExactly("Event Data (JSON)/configured/0", "House base/inherited_by_others/2");
        // No template parses XML fragments: said, with how to make one.
        assertThat(String.valueOf(found.get("xml_fragments"))).contains("replace_parser='XMLFragmentParser'");
        assertThat(templates.find("records").get("hint"))
                .isEqualTo("No template found for this stage; ask the user which pipeline to base it on.");
    }

    @Test
    void testCopyingATemplateIsRefusedInFavourOfInheriting() {
        add("t", "Event Data (Text)", "System/Template Pipelines", null);
        add("p", "Acme-V1-Events", "System/Feeds/Acme", "t");
        add("q", "Base", "System/Feeds", null);
        add("r", "Child", "System/Feeds", "q");
        assertThat(templates.templateReason("t")).isEqualTo("it is one of the template pipelines");
        assertThat(templates.templateReason("q")).isEqualTo("1 pipeline(s) inherit from it (Child)");
        assertThat(templates.templateReason("p")).isNull();
    }

    @Test
    void testTheContractIsWhatTheSharedXsltsRead() {
        xslt("dec", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                    + "<xsl:template match=\"EventSource/User\"><xsl:value-of select=\"Id\"/></xsl:template>"
                    + "</xsl:stylesheet>");
        PipelineLayers.given(pipelineService, "t", PipelineLayers.of(PipelineLayers.layer("t")
                .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter", "decorationFilter", "XSLTFilter",
                        "emptyFilter", "XSLTFilter", "schemaFilter", "SchemaFilter", "streamAppender",
                        "StreamAppender")
                .property("decorationFilter", "xslt", new DocRef(XsltDoc.TYPE, "dec", "Decorate"))
                .property("emptyFilter", "xslt", new DocRef(XsltDoc.TYPE, "empty", "Empty"))
                .property("schemaFilter", "schemaGroup", "EVENTS")
                .property("streamAppender", "streamType", "Events")));
        final Map<String, Object> contract = templates.contract("t");
        assertThat(contract.get("schema_group")).isEqualTo("EVENTS");
        assertThat(contract.get("output_stream_type")).isEqualTo("Events");
        final List<?> readers = (List<?>) contract.get("shared_readers");
        assertThat(readers).hasSize(2);
        final Map<?, ?> decoration = (Map<?, ?>) readers.get(0);
        assertThat(decoration.get("reads_elements")).isEqualTo(List.of("EventSource", "Id", "User"));
        // An empty decoration XSLT is common: said for that element, the rest of the contract still given.
        assertThat(((Map<?, ?>) readers.get(1)).get("unreadable")).isEqualTo("the document cannot be read "
                                                                             + "(deleted, or no permission)");
    }

    private static List<PipelineLayer> jsonTranslationTemplate(final String uuid) {
        return PipelineLayers.of(PipelineLayers.layer(uuid)
                .chain("jsonParser", "JSONParser", "translationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter")
                .property("schemaFilter", "schemaGroup", "EVENTS"));
    }

    private void add(final String uuid, final String name, final String path, final String parent) {
        index.put(uuid, new Entry(uuid, name, path, parent, null));
    }

    private void unloadable(final String uuid, final String reason) {
        Mockito.when(pipelineService.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, uuid)))
                .thenThrow(new PipelineModelException(reason));
    }

    private void xslt(final String uuid, final String text) {
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class))
                .thenReturn(Optional.of(XsltDoc.builder().uuid(uuid).name(uuid).data(text).build()));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> candidates(final Map<String, Object> found) {
        return (List<Map<String, Object>>) found.get("candidates");
    }

    private static List<Object> names(final Map<String, Object> found) {
        return candidates(found).stream().map(c -> c.get("name")).toList();
    }
}
