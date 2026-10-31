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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// What a build's feed is translated by now, and what its XSLT looks up: the current translation, the note on its
/// lookups, and which of them a new mapping keeps.
///
/// Seen in testing: V1.3 of a Fortigate feed drafted from the sample lost V1.2's IP lookups.
class TestCurrentTranslations {

    private static final DocRef GEO = new DocRef("Feed", "geo", "GEO-IP");
    private static final DocRef LOADER = new DocRef(PipelineDoc.TYPE, "loader", "Reference Loader");
    private static final String XSLT = "<xsl:value-of select=\"stroom:lookup('GEOIP', $ip)\"/>"
                                       + "<xsl:value-of select=\"stroom:lookup( 'DNS', $ip)\"/>";

    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final PipelineDocs pipelineDocs = Mockito.mock(PipelineDocs.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final CurrentTranslations currentTranslations = new CurrentTranslations(() -> plan, facts,
            pipelineDocs, pipelineShapes, templates, documentAccess);

    @Test
    void theCurrentTranslationIsReadWithWhatItsXsltLooksUp() {
        Mockito.when(plan.worksFrom("b")).thenReturn(List.of("FG"));
        facts.translating = List.of(entry("name", "FG-V1.2", "uuid", "v12"));
        Mockito.when(pipelineDocs.read("v12")).thenReturn(previous());
        Mockito.when(pipelineShapes.merged("v12")).thenReturn(MergedPipeline.of(PipelineLayers.of(
                PipelineLayers.layer("v12").chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")
                        .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x12", "FG-Events-V1.2")))));
        Mockito.when(documentAccess.read(eq(new DocRef(XsltDoc.TYPE, "x12")), eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x12").name("FG-Events-V1.2").data(XSLT).build()));
        final Map<String, Object> called = entry("href", "IP Lookup", "function", "gs:geo", "prefix", "gs",
                "namespace", "http://gradata.com.au", "example", "gs:geo($ip)", "calls", 3, "used_by",
                List.of("FG-V1.2"));
        Mockito.when(templates.sharedXsltUsage(any())).thenReturn(List.of(called, entry("href", "IP Lookup",
                "document_contents", Map.of())));

        final Map<String, Object> current = currentTranslations.current("b").orElseThrow();

        assertThat(current).isEqualTo(Map.of(
                "pipeline", Map.of("name", "FG-V1.2", "uuid", "v12"),
                "xslt", List.of(Map.of("name", "FG-Events-V1.2", "uuid", "x12")),
                "reference_data", List.of("GEO-IP via Reference Loader on translationFilter"),
                "lookup_maps", List.of("DNS", "GEOIP"),
                "functions_called", List.of(Map.of("href", "IP Lookup", "function", "gs:geo", "example",
                        "gs:geo($ip)")),
                "functions_entries", List.of(Map.of("href", "IP Lookup", "prefix", "gs", "namespace",
                        "http://gradata.com.au"))));
        final String note = CurrentTranslations.lookupsNote(current);
        assertThat(note).startsWith("The feed is translated now by 'FG-V1.2' (v12), whose XSLT looks up reference "
                                    + "map(s) ['DNS', 'GEOIP'] with stroom:lookup(); calls gs:geo() from 'IP "
                                    + "Lookup' (as gs:geo($ip))")
                .contains("Ask the user whether the new version keeps these lookups")
                .contains("functions entries [{'href': 'IP Lookup', 'prefix': 'gs', 'namespace': "
                          + "'http://gradata.com.au'}]")
                .contains("create_pipeline version_of='v12' keep_references=true");
        assertThat(CurrentTranslations.lookupsOf(current)).isTrue();
    }

    @Test
    void aFeedNothingTranslatesOrNoBuildHasNoCurrentTranslation() {
        Mockito.when(plan.worksFrom("b")).thenReturn(List.of());
        assertThat(currentTranslations.current("b")).isEmpty();
        assertThat(currentTranslations.current(null)).isEmpty();
        // A Stroom that can't say: advice, so never a reason for the call to fail.
        Mockito.when(plan.worksFrom("c")).thenThrow(new ToolRefusal("no record"));
        assertThat(currentTranslations.current("c")).isEmpty();
    }

    @Test
    void aPipelineWhoseDocumentsCantBeReadIsNamedAlone() {
        Mockito.when(plan.worksFrom("b")).thenReturn(List.of("FG"));
        facts.translating = List.of(entry("name", "FG-V1.2", "uuid", "v12"));
        Mockito.when(pipelineDocs.read("v12")).thenThrow(new ToolRefusal("Pipeline v12 can't be read"));

        final Map<String, Object> current = currentTranslations.current("b").orElseThrow();

        assertThat(current).isEqualTo(Map.of("pipeline", Map.of("name", "FG-V1.2", "uuid", "v12")));
        assertThat(CurrentTranslations.lookupsOf(current)).isFalse();
    }

    @Test
    void aMappingKeepsTheLookupsByALookupEntryOrTheSharedXsltItCalls() {
        final Map<String, Object> current = current();
        final Map<String, Object> base = new LinkedHashMap<>();
        base.put("input", "json");
        base.put("common", List.of(Map.of("path", "EventSource/System/Name", "value", "FortiOS")));
        base.put("events", List.of(Map.of("name", "all", "fields", List.of(Map.of("path", "EventDetail/TypeId",
                "value", "x")))));
        assertThat(CurrentTranslations.lookupsKept(current, TranslationMapping.parse(base))).isFalse();

        final Map<String, Object> calling = new LinkedHashMap<>(base);
        calling.put("functions", current.get("functions_entries"));
        assertThat(CurrentTranslations.lookupsKept(current, TranslationMapping.parse(calling))).isTrue();

        final Map<String, Object> looking = new LinkedHashMap<>(base);
        final List<Object> common = new ArrayList<>((List<?>) base.get("common"));
        common.add(Map.of("path", "EventSource/Device/Location/Country", "lookup", Map.of("map", "GEOIP",
                "field", "srcip")));
        looking.put("common", common);
        assertThat(CurrentTranslations.lookupsKept(current, TranslationMapping.parse(looking))).isTrue();

        assertThat(CurrentTranslations.lookupsOf(current)).isTrue();
        assertThat(CurrentTranslations.lookupsOf(Map.of("pipeline", current.get("pipeline")))).isFalse();
        assertThat(CurrentTranslations.lookupsOf(null)).isFalse();
    }

    @Test
    void aPipelinesOwnReferencesAreThoseWithAFeedAndALoader() {
        final PipelineDoc previous = previous();
        assertThat(CurrentTranslations.ownReferences(previous)).singleElement()
                .satisfies(reference -> assertThat(CurrentTranslations.referenceText(reference))
                        .isEqualTo("GEO-IP via Reference Loader on translationFilter"));
        assertThat(CurrentTranslations.ownReferences(PipelineDoc.builder().uuid("p").name("P").build())).isEmpty();
        assertThat(CurrentTranslations.lookupMaps(XSLT)).containsExactly("GEOIP", "DNS");
        assertThat(CurrentTranslations.lookupMaps(null)).isEmpty();
    }

    // The feed's current translation.
    private static Map<String, Object> current() {
        final Map<String, Object> current = new LinkedHashMap<>();
        current.put("pipeline", Map.of("uuid", "v12", "name", "FG-V1.2"));
        current.put("xslt", List.of(Map.of("name", "FG-Events-V1.2", "uuid", "x12")));
        current.put("reference_data", List.of("GEO-IP via Reference Loader on translationFilter"));
        current.put("lookup_maps", List.of("GEOIP"));
        current.put("functions_called", List.of(Map.of("href", "IP Lookup", "function", "gs:geo", "example",
                "gs:geo($ip)")));
        current.put("functions_entries", List.of(Map.of("href", "IP Lookup", "prefix", "gs", "namespace",
                "http://gradata.com.au")));
        return current;
    }

    // V1.2: loading GEO-IP through the loader for its lookups, and one reference with no loader.
    private static PipelineDoc previous() {
        return PipelineDoc.builder().uuid("v12").name("FG-V1.2")
                .pipelineData(PipelineLayers.layer("v12")
                        .reference("translationFilter", GEO, LOADER)
                        .reference("translationFilter", GEO, null)
                        .data())
                .build();
    }

    private static Map<String, Object> entry(final Object... namesAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            map.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        }
        return map;
    }
}
