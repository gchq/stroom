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

import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// Shared XSLTs standing instructions require (the tools' cases; reading the section is `InstructionRules`', the
/// generator writing imports first the generators' own).
/// Asked for by the user: every Raw Events to Events translation imports a Stream-Meta-V1 translation: an AGENTS doc
/// says so per stage, the generator adds the import, and an XSLT written by hand without it is refused.
class TestRequiredImports {

    private static final String HAND = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                       + "xmlns=\"event-logging:3\" version=\"3.0\"><xsl:template match=\"/\"><Events/>"
                                       + "</xsl:template></xsl:stylesheet>";
    private static final String GEOIP = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:geo="http://example.com/geo"
              xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0">
              <xsl:function name="geo:lookup-ip" as="xs:string"><xsl:param name="ip" as="xs:string"/>\
            <xsl:sequence select="$ip"/></xsl:function>
              <xsl:function name="geo:document"><xsl:param name="ip"/><xsl:sequence select="$ip"/></xsl:function>
            </xsl:stylesheet>""";

    private final TranslationWorld world = new TranslationWorld();
    private final ToolSession session = world.context().session();

    @Test
    void anXsltWrittenByHandWithoutARequiredImportIsRefused() {
        require(InstructionRules.TRANSLATION, "Stream-Meta-V1");

        assertThatThrownBy(() -> world.requiredImports.refuseWithout(session, HAND, "b", null))
                .hasMessageContaining("require every translation XSLT to import 'Stream-Meta-V1'")
                .hasMessageContaining("<xsl:import href=\"Stream-Meta-V1\"/>");
        // Has it: fine.
        world.requiredImports.refuseWithout(session, HAND.replace("version=\"3.0\">",
                "version=\"3.0\"><xsl:import href=\"Stream-Meta-V1\"/>"), "b", null);
    }

    @Test
    void eachStageIsHeldToItsOwnRequiredImports() {
        // The AGENTS doc requires an import of indexing XSLTs only.
        require(InstructionRules.INDEXING, "Common-Index-V1");

        assertThatThrownBy(() -> world.requiredImports.refuseWithout(session, "<records xmlns=\"records:2\"/>", "b",
                "Indexing")).hasMessageContaining("every indexing XSLT to import 'Common-Index-V1'");
        world.requiredImports.refuseWithout(session, "<records/>", "b", "Records");

        final ArgumentCaptor<String> stages = ArgumentCaptor.forClass(String.class);
        Mockito.verify(world.agentInstructions, Mockito.times(2)).importsRequired(any(), stages.capture(), any(),
                any());
        assertThat(stages.getAllValues()).isEqualTo(List.of("indexing", "records"));
    }

    @Test
    void aReferenceXsltBuiltForABuildGetsTheRequiredImports() {
        require(InstructionRules.REFERENCE, "Ref-Common-V1");
        final BuildReferenceXsltTool tool = new BuildReferenceXsltTool(world.heldSchemas, world.requiredImports);

        final Map<String, Object> built = world.call(tool, Map.of("mapping", Map.of("input", "json", "maps",
                List.of(Map.of("name", "HOSTS", "key", "host", "values", List.of(Map.of("field", "ip"))))),
                "schema_version", "2.0.1", "build", "b"));

        assertThat(String.valueOf(built.get("xslt"))).contains("<xsl:import href=\"Ref-Common-V1\"/>");
        assertThat((List<?>) built.get("required_imports")).hasSize(1);
        Mockito.verify(world.agentInstructions).importsRequired(any(), eq("reference"), any(), any());
    }

    @Test
    void aRequiredImportsFunctionsAreOfferedWithTheEntryThatBindsThem() {
        // An AGENTS doc can require a shared XSLT for its functions, not only its templates.
        final DocRef geo = world.inBuild("shared", XsltDoc.TYPE, "GeoIP-V1");
        world.world.docs.put(geo.getUuid(), world.xslt(geo.getUuid()).copy().data(GEOIP).build());
        Mockito.when(world.docFinder.findByName(eq(XsltDoc.TYPE), eq("GeoIP-V1"), anyBoolean()))
                .thenReturn(List.of(geo));
        final List<RequiredImport> required = List.of(new RequiredImport("GeoIP-V1", "/Feeds"));

        final Map<String, Object> note = world.requiredImports.importNotes(required, true).getFirst();
        final Map<String, Object> reference = world.requiredImports.importNotes(required, false).getFirst();

        assertThat(note.get("functions")).isEqualTo(Map.of(
                "geo:lookup-ip", Map.of("params", List.of("ip as xs:string"), "returns", "xs:string"),
                "geo:document", Map.of("params", List.of("ip"))));
        assertThat(note.get("functions_entries")).isEqualTo(List.of(Map.of("href", "GeoIP-V1", "prefix", "geo",
                "namespace", "http://example.com/geo")));
        assertThat(note).containsEntry("required_by", "the AGENTS doc in /Feeds");
        // A reference mapping has no functions entries to call them with.
        assertThat(reference).doesNotContainKey("functions");
    }

    @Test
    void aRequiredImportStroomDoesntHoldIsSaid() {
        final Map<String, Object> note = world.requiredImports.importNotes(
                List.of(new RequiredImport("Missing-V1", "/")), false).getFirst();

        assertThat(note).containsEntry("required_by", "the AGENTS doc in (root)")
                .containsEntry("problem", "no XSLT named 'Missing-V1' in Stroom: the import will fail until it "
                                          + "exists");
    }

    private void require(final String stage, final String href) {
        Mockito.when(world.agentInstructions.importsRequired(any(), anyString(), any(), any())).thenAnswer(inv ->
                stage.equals(inv.getArgument(1))
                        ? List.of(new RequiredImport(href, "System"))
                        : List.of());
    }
}
