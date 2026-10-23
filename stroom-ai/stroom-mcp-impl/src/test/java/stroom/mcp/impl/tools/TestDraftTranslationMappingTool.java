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

import stroom.ai.impl.tools.sample.PyJson;
import stroom.documentation.shared.DocumentationDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// draft_translation_mapping, the tool (the Stroom MCP server's `tools/generation.py` `draft_translation_mapping`,
/// and the tool parts of `tests/test_generation.py`, `test_draft_reply.py` and `test_xml_fragments.py`; the draft
/// itself is [stroom.ai.impl.tools.draft.DraftMapping]'s, tested with it).
class TestDraftTranslationMappingTool {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n"
                                       + "2026-10-01T10:05:00Z,bob,logout\n";
    // test_draft_reply.py's firewall sample.
    private static final String FIREWALL = """
            timestamp,device,event_type,severity,src_ip,src_port,dst_ip,dst_port,protocol,action,rule_id,bytes_sent,\
            bytes_received,username,message
            2026-10-01T09:00:12+10:00,FW1,TRAFFIC,INFO,192.0.2.10,54321,198.51.100.20,443,TCP,ALLOW,1001,1520,8450,,ok
            2026-10-01T09:01:05+10:00,FW1,TRAFFIC,WARNING,203.0.113.45,49822,192.0.2.25,22,TCP,DENY,2003,0,0,,blocked
            2026-10-01T09:10:00+10:00,FW1,ADMIN,INFO,192.0.2.5,50000,192.0.2.1,443,HTTPS,LOGIN_SUCCESS,0,0,0,admin,in
            2026-10-01T09:20:00+10:00,FW1,SYSTEM,WARNING,,,,,N/A,HIGH_CPU,0,0,0,,CPU over 85 percent
            """;

    private final TranslationWorld world = new TranslationWorld();
    private final DraftTranslationMappingTool tool = world.draftTool();

    @Test
    void toolsReadTheSampleFromItsStreamsInsteadOfItsText() {
        // The text goes through the model once, to upload_sample; the tools after it take stream_ids.
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", CSV), List.of()));

        final Map<String, Object> draft = world.call(tool, Map.of("stream_ids", List.of(7)));

        assertThat(((Map<?, ?>) draft.get("mapping")).get("input")).isEqualTo("data_splitter");
        Mockito.verify(world.sampleStreamTexts).read(List.of(7L));
    }

    @Test
    void neitherSamplesNorStreamsIsRefused() {
        assertThat(world.call(tool, Map.of("source_name", "Acme")))
                .containsEntry("error", "Give the samples' text, or stream_ids of the uploaded sample streams");
    }

    @Test
    void theDraftIsCheckedAgainstTheSchemaAndKeptForTheSession() {
        // Kept for this session, so the first save sends only what changes (seen in testing: a 50 KB draft,
        // written out whole in one call, never reached the server).
        final Map<String, Object> draft = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1"));

        assertThat(draft).containsEntry("schema_version", TranslationWorld.VERSION).containsKey("schema_check");
        assertThat(((Map<?, ?>) draft.get("schema_check")).containsKey("problems")).isTrue();
        assertThat(world.files()).containsKeys("/outputs/mappings/draft-acme-v1.json",
                "/outputs/mappings/draft.json");
        assertThat(PyJson.loads(world.files().get("/outputs/mappings/draft-acme-v1.json")))
                .isEqualTo(PyJson.loads(PyJson.dumps(draft.get("mapping"))));
        // The one version this Stroom holds was settled without the agent: the hint says so first.
        assertThat((String) draft.get("hint")).startsWith(TranslationWorld.VERSION + " is the only event-logging "
                                                          + "version this Stroom holds")
                .contains("the server keeps this draft for the session");
        // A change sent for the build and an XSLT name not saved yet starts from it.
        final TranslationBuilds.Base base = world.builds.mappingToChange(world.context(), "acme-v1/ACME-Events",
                null, "acme-v1", "ACME-Events");
        assertThat(base.basedOn()).isEqualTo(TranslationBuilds.ON_THE_DRAFT);
    }

    @Test
    void aNextVersionsDraftNamesWhatTheCurrentTranslationLooksUp() {
        // The Stroom MCP server's tests/test_new_version.py. Seen in testing: V1.3 drafted for a feed V1.2
        // translates, and its IP lookups were gone, though the user meant to keep them.
        Mockito.when(world.currentTranslations.current("acme-v1")).thenReturn(Optional.of(currentTranslation()));

        final Map<String, Object> draft = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1"));

        assertThat(draft).containsEntry("current_translation", currentTranslation());
        assertThat(((List<?>) draft.get("notes")).getLast()).asString()
                .startsWith("The feed is translated now by 'FG-V1.2' (v12), whose XSLT looks up reference map(s) "
                            + "['GEOIP'] with stroom:lookup(); loads reference data ['GEO-IP via Reference Loader "
                            + "on translationFilter']. Ask the user whether the new version keeps these lookups.")
                .contains("create_pipeline version_of='v12' keep_references=true");
        // A feed translated by a pipeline that looks nothing up, or no build: nothing to say.
        Mockito.when(world.currentTranslations.current("acme-v1")).thenReturn(Optional.of(Map.of("pipeline",
                Map.of("name", "FG-V1.2", "uuid", "v12"))));
        assertThat(world.call(tool, Map.of("samples", Map.of("acme.csv", CSV), "build", "acme-v1")))
                .doesNotContainKey("current_translation");
        assertThat(world.call(tool, Map.of("samples", Map.of("acme.csv", CSV))))
                .doesNotContainKey("current_translation");
    }

    @Test
    void theSessionsDraftFitsTheClientInline() {
        // Copilot saved a 10.6 KB draft to a file; the agent read 100 of its 502 lines and wrote its own mapping.
        final Map<String, Object> draft = world.call(tool, Map.of("samples", List.of(FIREWALL),
                "source_name", "Firewall Logs"));

        final Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("mapping", draft.get("mapping"));
        reply.put("schema_check", draft.get("schema_check"));
        assertThat(PyJson.dumpsCompact(reply).length()).isLessThan(8000);
    }

    @Test
    void theDraftFollowsTheBuildsSourceNotes() {
        // The user's documentation, kept in the build by record_source_notes: the draft follows its catalogue.
        final String notes = world.inBuild("acme-v1", DocumentationDoc.TYPE, "Acme source notes").getUuid();
        world.world.docs.put(notes, DocumentationDoc.builder().uuid(notes).name("Acme source notes").version("v0")
                .data("# Acme source notes\n\n<!-- stroom-mcp source notes\n{\"source\": \"Acme\", \"fields\": [], "
                      + "\"events\": [{\"event\": \"login\", \"description\": \"A user logged in\", "
                      + "\"event_detail\": \"Authenticate\", \"type_id\": \"Logon\", \"field\": \"action\", "
                      + "\"value\": \"login\", \"action\": \"Logon\"}]}\n-->\n")
                .build());

        final Map<String, Object> draft = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1"));

        assertThat(PyJson.dumps(draft.get("mapping"))).contains("EventDetail/Authenticate");
    }

    @Test
    void xmlFragmentsAreDraftedInTheNamespaceOfTheirWrapper() {
        // Read in the namespace of the wrapper the environment uses, which need not be records:2: with no wrapper
        // of the environment's own, the standard records:2 one.
        final Map<String, Object> draft = world.call(tool, Map.of("samples", Map.of("events.xml",
                TestSampleProfiling.FRAGMENTS)));

        final Map<?, ?> mapping = (Map<?, ?>) draft.get("mapping");
        assertThat(mapping.get("input")).isEqualTo("xml_fragments");
        assertThat(mapping.get("xml_namespace")).isEqualTo("records:2");
    }

    @Test
    void aSampleThatIsAFilePathIsRefusedWithWhatToSend() {
        final Map<String, Object> refused = world.call(tool, Map.of("samples", List.of("C:\\logs\\acme.csv")));

        assertThat((String) refused.get("error")).contains("path");
    }

    // A feed translated now by FG-V1.2, whose XSLT looks up GEOIP and loads its reference data.
    private static Map<String, Object> currentTranslation() {
        final Map<String, Object> current = new LinkedHashMap<>();
        current.put("pipeline", Map.of("name", "FG-V1.2", "uuid", "v12"));
        current.put("xslt", List.of(Map.of("name", "FG-Events-V1.2", "uuid", "x12")));
        current.put("reference_data", List.of("GEO-IP via Reference Loader on translationFilter"));
        current.put("lookup_maps", List.of("GEOIP"));
        return current;
    }
}
