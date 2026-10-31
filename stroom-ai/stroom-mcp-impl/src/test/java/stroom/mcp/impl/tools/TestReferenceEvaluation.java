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

import stroom.ai.impl.tools.MergedPipeline;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;

/// evaluate_pipeline's `reference` section, and write_documentation's Reference maps section made from it, on
/// [SteppingWorld]'s pipeline ACME, whose own XSLT `x` writes the map.
class TestReferenceEvaluation {

    private static final String XSLT = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                       + "xmlns=\"reference-data:2\" version=\"2.0\"><xsl:template match=\"record\">"
                                       + "<reference><map>USER_TO_NAME</map><key><xsl:value-of select=\"data[@name="
                                       + "'user']/@value\"/></key><value><xsl:value-of select=\"data[@name='name']"
                                       + "/@value\"/></value></reference></xsl:template></xsl:stylesheet>";
    private static final String WRITTEN = "<referenceData xmlns=\"reference-data:2\"><reference><map>USER_TO_NAME"
                                          + "</map><key>alice</key><value>Alice Smith</value></reference>"
                                          + "<reference><map>USER_TO_NAME</map><key>bob</key><value>Bob Jones"
                                          + "</value></reference></referenceData>";

    private final SteppingWorld world = new SteppingWorld();
    private final ReferenceData referenceData = Mockito.mock(ReferenceData.class);
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final ReferenceEvaluation evaluation = new ReferenceEvaluation(world.documentAccess, referenceData,
            processorFilters);
    private final MergedPipeline merged;

    TestReferenceEvaluation() {
        Mockito.when(world.documentAccess.read(any(DocRef.class), Mockito.eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x").name("USERS-Reference").data(XSLT).build()));
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(true);
        Mockito.when(processorFilters.all()).thenReturn(List.of());
        Mockito.when(processorFilters.feedsByPipeline(anyList())).thenReturn(Map.of("p-1", Set.of("USERS")));
        Mockito.when(referenceData.referencesTo(anySet(), any(), anyList())).thenReturn(List.of(Map.of(
                "pipeline", "LOGONS-Events", "uuid", "e", "feed", "USERS", "loader", "Reference Loader")));
        merged = MergedPipeline.load(world.documentAccess, SteppingWorld.UUID).orElseThrow();
    }

    @Test
    void itsMapsAreGivenWithWhatSteppingSawItsFeedsAndWhoLooksThemUp() {
        final Map<String, Object> section = evaluation.reference(merged, SteppingWorld.UUID, List.of(WRITTEN));

        assertThat(section).containsEntry("status", "checked")
                .containsEntry("feeds", List.of("USERS"));
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = ((List<Map<String, Object>>) section.get("maps")).getFirst();
        assertThat(map).containsEntry("map", "USER_TO_NAME")
                .containsEntry("key", "data[@name='user']/@value")
                .containsEntry("value", "text")
                .containsEntry("xslt", "USERS-Reference")
                .containsEntry("seen", Map.of("entries", 2, "example_key", "alice", "example_value", "Alice Smith"));
        assertThat(section.get("looked_up_by").toString()).contains("LOGONS-Events");
        Mockito.verify(referenceData).referencesTo(Set.of("USERS"), SteppingWorld.UUID, List.of());

        final String markdown = ReferenceEvaluation.markdown(section, Map.of("12", 2));
        assertThat(markdown).startsWith("Counts are of 2 sample records (2 of stream 12).")
                .contains("| USER_TO_NAME | data[@name='user']/@value | text | 2 | alice | Alice Smith |")
                .contains("Loaded from feed(s): USERS.")
                .contains("Looked up by: LOGONS-Events (feed USERS).");
    }

    @Test
    void withoutManageProcessorsOrStepsItSaysWhatIsMissing() {
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(false);
        Mockito.when(referenceData.referencesTo(anySet(), any(), anyList())).thenReturn(List.of());

        final Map<String, Object> section = evaluation.reference(merged, SteppingWorld.UUID, null);

        assertThat(section).containsEntry("status", "checked").containsEntry("feeds", List.of());
        assertThat(section.get("notes").toString()).contains("Manage Processors", "Nothing was stepped",
                "nothing looks its maps up yet");
        // Only as the loader named: no feeds to match.
        Mockito.verify(referenceData).referencesTo(Set.of(), SteppingWorld.UUID, List.of());
    }

    @Test
    void mapsAreReadFromWhatWasWrittenToo() {
        assertThat(ReferenceEvaluation.seen(List.of(WRITTEN, "not xml"))).isEqualTo(Map.of("USER_TO_NAME",
                Map.of("entries", 2, "example_key", "alice", "example_value", "Alice Smith")));
    }
}
