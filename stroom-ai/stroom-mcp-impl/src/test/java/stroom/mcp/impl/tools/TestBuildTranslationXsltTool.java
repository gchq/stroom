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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// build_translation_xslt (the Stroom MCP server's `tests/test_generation.py`, `tests/test_build_feedback.py`,
/// `tests/test_changes_on_draft.py` and `tests/test_mapping_changes.py`, their tool cases, with the tool cases of
/// `tests/test_authoring_guards.py` and `tests/test_draft.py` that call it).
class TestBuildTranslationXsltTool {

    private static final String SAMPLE = """
            time,action,user,result,sid,host,port
            2026-09-28T10:00:00.000Z,login,alice,ok,s1,ws01,22
            2026-09-28T10:01:00.000Z,status,,,,ws01,0
            2026-09-28T10:02:00.000Z,status,,,,ws02,0
            2026-09-28T10:03:00.000Z,login,bob,fail,s2,ws03,22
            """;
    // test_build_feedback.py's GOOD: a JSON mapping that generates.
    private static final String GOOD = """
            {"input": "json", "common": [{"path": "EventTime/TimeCreated", "field": "ts"},
                                         {"path": "EventSource/System/Name", "value": "Acme"},
                                         {"path": "EventSource/System/Environment", "value": "Test"},
                                         {"path": "EventSource/Generator", "value": "acme"},
                                         {"path": "EventSource/Device/HostName", "field": "host"}],
             "events": [{"name": "logon",
                         "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                                    {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                    {"path": "EventDetail/Authenticate/User/Id", "field": "user"}]}]}""";
    // test_draft.py's FW_JSON and INVENTORY.
    private static final String FW_JSON = "{\"timestamp\":\"2026-10-01T09:00:12+10:00\",\"device\":\"FW-EDGE-01\","
                                          + "\"event_type\":\"TRAFFIC\",\"severity\":\"INFO\","
                                          + "\"src_ip\":\"192.0.2.10\","
                                          + "\"src_port\":54321,\"dst_ip\":\"198.51.100.20\",\"dst_port\":443,"
                                          + "\"protocol\":\"TCP\",\"action\":\"ALLOW\",\"rule_id\":1001,"
                                          + "\"bytes_sent\":1520,\"bytes_received\":8450,\"username\":\"\","
                                          + "\"message\":\"Outbound HTTPS connection allowed\"}\n"
                                          + "{\"timestamp\":\"2026-10-01T09:01:00+10:00\",\"device\":\"FW-EDGE-01\","
                                          + "\"event_type\":\"LOGIN\",\"severity\":\"INFO\","
                                          + "\"src_ip\":\"192.0.2.11\","
                                          + "\"src_port\":1,\"dst_ip\":\"198.51.100.1\",\"dst_port\":22,"
                                          + "\"protocol\":\"TCP\",\"action\":\"ALLOW\",\"rule_id\":7,"
                                          + "\"bytes_sent\":0,"
                                          + "\"bytes_received\":0,\"username\":\"alice\","
                                          + "\"message\":\"Admin login\"}\n";
    private static final List<Map<String, Object>> SYSTEM = List.of(Map.of("path", "EventSource/System/Name",
            "value", "FortiOS"));

    private final TranslationWorld world = new TranslationWorld();
    private final BuildTranslationXsltTool tool = world.buildTool();

    @Test
    void noFieldMappingWithoutASample() {
        final Map<String, Object> result = world.call(tool, Map.of("mapping", TranslationWorld.mapping()));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat((String) result.get("xslt")).startsWith("<?xml");
        // A table from the mapping alone shows how values are computed; it used to be copied into docs as it was.
        assertThat(result).doesNotContainKey("field_mapping");
        assertThat((String) result.get("field_mapping_needs")).contains("pipeline_uuid and stream_ids");
    }

    @Test
    void aSingleStreamIdIsTakenAsAListOfOne() {
        // Clients send the model's arguments as they are; one sample stream often arrives as a bare number, which
        // failed validation ("invalid input") and left the documentation to be written by hand.
        Mockito.when(world.sampleStreamTexts.read(any())).thenReturn(new SampleStreamTexts.Texts(Map.of(), List.of()));
        final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
        Mockito.when(pipeline.defaultOutputs()).thenReturn(List.of("translationFilter"));
        Mockito.when(world.stepping.load("p-1")).thenReturn(pipeline);
        Mockito.when(world.stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of(), false));

        final Map<String, Object> result = world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "pipeline_uuid", "p-1", "stream_ids", 15783601, "field_mapping", true));

        assertThat(result).doesNotContainKey("error");
        Mockito.verify(world.stepping).outputs(any(), eq(pipeline), eq(List.of(15783601L)), eq("translationFilter"),
                any(), eq(50));
    }

    @Test
    void savingReturnsTheDocumentNotTheCode() {
        // The XSLT need not pass through the model: generated, saved with its mapping, referred to by uuid.
        final Map<String, Object> saved = world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "build", "acme-v1", "name", "ACME-Events"));

        assertThat(saved).doesNotContainKey("xslt");
        final Map<String, Object> doc = map(saved.get("saved"));
        assertThat(doc).containsEntry("type", XsltDoc.TYPE).containsEntry("name", "ACME-Events");
        final String uuid = String.valueOf(doc.get("uuid"));
        assertThat((String) saved.get("hint")).contains("uuid='" + uuid + "'");
        final XsltDoc xslt = world.xslt(uuid);
        assertThat(xslt.getData()).startsWith("<?xml");
        // Kept with its mapping, and tagged so a mapping deleted later is noticed.
        assertThat(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().kind()).isEqualTo("translation");
        assertThat(world.world.tags(uuid)).contains(WriteGuard.KEPT_MAPPING);

        final Map<String, Object> again = world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "uuid", uuid, "include_xslt", true));
        assertThat((String) again.get("xslt")).startsWith("<?xml");
        assertThat(map(again.get("saved")).get("version")).isNotEqualTo(doc.get("version"));

        final Map<String, Object> broken = new LinkedHashMap<>(TranslationWorld.mapping());
        broken.put("events", List.of(Map.of("name", "bare", "fields", List.of(Map.of("path", "EventDetail/Nope",
                "value", "x")))));
        final Map<String, Object> failed = world.call(tool, Map.of("mapping", broken, "build", "acme-v1",
                "name", "Other"));
        assertThat(failed.get("ok")).isEqualTo(false);
        assertThat(failed).doesNotContainKey("saved");

        assertThat(world.call(tool, Map.of("mapping", TranslationWorld.mapping(), "build", "acme-v1")).get("error"))
                .asString().contains("give name");
    }

    @Test
    void aSaveThatKeepsNoneOfTheCurrentTranslationsLookupsWarnsNamingThem() {
        // The Stroom MCP server's FINDINGS (0.18.41): a next version written anew lost the lookups, and nothing said
        // so. Unless the user chose to drop them, the save says what the current translation looks up.
        Mockito.when(world.currentTranslations.current("acme-v1")).thenReturn(Optional.of(currentTranslation()));

        final Map<String, Object> saved = world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "build", "acme-v1", "name", "ACME-Events"));

        assertThat(saved).containsKey("saved");
        assertThat(String.valueOf(saved.get("warnings")))
                .contains("This mapping keeps none of the current translation's lookups. The feed is translated now "
                          + "by 'FG-V1.2' (v12)");
        // Nothing to keep: no warning.
        Mockito.when(world.currentTranslations.current("acme-v1")).thenReturn(Optional.of(Map.of("pipeline",
                Map.of("name", "FG-V1.2", "uuid", "v12"))));
        assertThat(String.valueOf(world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "build", "acme-v1", "name", "ACME-Events-2")).get("warnings")))
                .doesNotContain("keeps none of the current translation's lookups");
    }

    @Test
    void anXsltThatDoesNotCompileIsNotSavedAndSaysWhy() {
        // Seen: saved ok:true with an xpath of $kv, then every record failed at stepping ("Variable kv has not been
        // declared"). Compiled here as Stroom compiles it.
        Mockito.when(world.compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of(
                "XPST0008 Variable $device_name has not been declared")));

        final Map<String, Object> result = world.call(tool, Map.of("mapping", TranslationWorld.mapping(),
                "build", "acme-v1", "name", "ACME-Events"));

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result).doesNotContainKey("saved");
        assertThat((List<?>) result.get("problems")).anySatisfy(p -> assertThat(String.valueOf(p))
                .contains("doesn't compile").contains("XPST0008 Variable $device_name has not been declared"));
    }

    @Test
    void toolsReadTheSampleFromItsStreamsInsteadOfItsText() {
        // The text goes through the model once, to upload_sample; the tools after it take stream_ids.
        final String csv = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n2026-10-01T10:05:00Z,bob,logout\n";
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", csv), List.of()));
        final Map<String, Object> typo = TranslationWorld.mapping();
        final List<Object> common = new ArrayList<>(list(typo.get("common")));
        common.set(0, Map.of("path", "EventTime/TimeCreated", "field", "tiem"));
        typo.put("common", common);

        final Map<String, Object> checked = world.call(tool, Map.of("mapping", typo, "stream_ids", List.of(7),
                "splitter", Map.of("kind", "delimited", "header", true)));

        // Checked against the stream's records: a near miss of a sample field is a problem, so nothing is generated.
        assertThat(checked.get("ok")).isEqualTo(false);
        assertThat((List<?>) checked.get("problems")).anySatisfy(p -> assertThat(String.valueOf(p))
                .contains("'tiem'").contains("did you mean ['time']"));
        Mockito.verify(world.sampleStreamTexts).read(List.of(7L));
    }

    @Test
    void theRuleForTheRestKeptUnknownIsPutAsTheRecordsNoOtherRuleMatches() {
        final Map<String, Object> mapping = TranslationWorld.mapping();
        final List<Object> events = new ArrayList<>(list(mapping.get("events")));
        final Map<String, Object> rest = new LinkedHashMap<>(map(events.get(1)));
        rest.put("allow_unknown", "status lines carry no activity");
        events.set(1, rest);
        mapping.put("events", events);
        final String sample = "time,action,user,result,sid,host,port\n2026-09-28T10:00:00.000Z,login,alice,ok,s1,ws01,"
                              + "22\n2026-09-28T10:01:00.000Z,status,,,,ws01,0\n";

        final Map<String, Object> shown = world.call(tool, Map.of("mapping", mapping, "build", "acme-v1", "name", "N",
                "sample", sample));

        assertThat(shown.get("status")).isEqualTo("needs_review");
        assertThat((String) shown.get("show_user")).contains("**Records no other rule matches** (rule `other`)\n\n- 1 "
                                                             + "of the 2 sample records\n");
    }

    @Test
    void keepingUnknownNeedsTheUsersConfirmationBeforeSaving() {
        final Map<String, Object> kept = keptAsUnknown();
        // Seen in VS Code: asked with "no sample records checked", the user couldn't see what went to Unknown.
        assertThat(world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N")).get("error")).asString()
                .contains("call again with stream_ids");
        // Seen in VS Code: "Keep rest as Unknown (what happened is not known) in the saved XSLT: rest:" was
        // cryptic, and the detail too much tiny text in a form (asked for by the user): shown in the chat first,
        // the form short.
        final Map<String, Object> shown = world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N",
                "sample", SAMPLE));
        assertThat(shown.get("status")).isEqualTo("needs_review");
        final String reviewId = String.valueOf(shown.get("review_id"));
        assertThat((String) shown.get("hint")).contains("reviewed='" + reviewId + "'");
        // Not passed by saying it was reviewed: only by the id of what was shown (seen in promote_build).
        final Map<String, Object> again = world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N",
                "sample", SAMPLE, "reviewed", true));
        assertThat(again.get("status")).isEqualTo("needs_review");
        assertThat((String) again.get("hint")).contains("true is not one");
        final String md = String.valueOf(shown.get("show_user"));
        assertThat(md).startsWith("**Records to translate as Unknown events**\n\nAn Unknown event says what happened "
                                  + "isn't known: it has no Authenticate, Create or other action.\n\n**Records rule "
                                  + "`status` matches**\n\n- 2 of the 4 sample records\n- What they hold:\n  - ")
                .contains("  - action: status\n")
                .endsWith("- Reason given: status lines carry no activity\n");
        // Without a form the user can't agree: nothing is saved, and no id is given to pass back.
        final Map<String, Object> refused = world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N",
                "sample", SAMPLE, "reviewed", reviewId));
        assertThat(refused.get("error")).asString()
                .startsWith("Not done: Translate the records shown in the chat as Unknown events.")
                .doesNotContain("confirmation_id");
        world.forms = true;
        world.answers.put("Translate the records", "yes");
        final Map<String, Object> done = world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N",
                "sample", SAMPLE, "reviewed", reviewId));
        assertThat(world.shown).singleElement().asString()
                .startsWith("Translate the records shown in the chat as Unknown events")
                .contains("- rule 'status': 2 of 4 sample records", "- as shown in the chat: ");
        assertThat(done).as(String.valueOf(done)).containsKey("saved");
        final String uuid = String.valueOf(map(done.get("saved")).get("uuid"));
        assertThat(world.xslt(uuid)).isNotNull();
        // Checking without saving asks nothing.
        assertThat(world.call(tool, Map.of("mapping", kept))).doesNotContainKey("status");
        // A rule keeping none of the sample Unknown holds nothing to agree to: saved without asking (seen: the user
        // asked to keep 'other' as Unknown for "none of the 1000" records).
        final String logins = SAMPLE.replace(",status,,,,", ",login,carol,ok,s3,");
        assertThat(world.call(tool, Map.of("mapping", kept, "build", "acme-v1", "name", "N2", "sample", logins)))
                .containsKey("saved");

        // Asked once per rule (seen in production: asked again on every save). Saved again with the same rule,
        // nothing is asked; with its conditions or reason changed, it is.
        final Map<String, Object> resaved = world.call(tool, Map.of("mapping", kept, "uuid", uuid, "sample",
                SAMPLE));
        assertThat(map(resaved.get("saved")).get("uuid")).isEqualTo(uuid);
        // Not asked, but on the record: kept on the user's earlier answer.
        Mockito.verify(world.auditLog).processed(any(), any(), eq("confirmation"), anyString(),
                eq("granted (earlier): [status]"), eq(true));
        final List<Object> events = new ArrayList<>(list(kept.get("events")));
        final Map<String, Object> status = new LinkedHashMap<>(map(events.get(1)));
        status.put("allow_unknown", "heartbeats only");
        events.set(1, status);
        final Map<String, Object> changed = new LinkedHashMap<>(kept);
        changed.put("events", events);
        assertThat(world.call(tool, Map.of("mapping", changed, "uuid", uuid, "sample", SAMPLE)).get("status"))
                .isEqualTo("needs_review");
    }

    @Test
    void aFieldAnInferredSplitterLacksIsAWarningNotARefusal() {
        // Seen: a hand-written syslog splitter names a field 'pwd'; the sample, read with the spec inferred from
        // it, has 'pid' instead, and the mapping was refused though the build's converter does write 'pwd'.
        final Map<String, Object> mapping = TranslationWorld.mapping();
        final List<Object> common = new ArrayList<>(list(mapping.get("common")));
        common.add(Map.of("path", "EventSource/Device/Name", "field", "hosts"));
        mapping.put("common", common);
        final String sample = "time,action,user,result,sid,host,port\n2026-09-28T10:00:00.000Z,login,alice,ok,s1,ws01,"
                              + "22\n2026-09-28T10:03:00.000Z,login,bob,fail,s2,ws03,22\n";

        final Map<String, Object> guessed = world.call(tool, Map.of("mapping", mapping, "sample", sample));
        assertThat(guessed.get("ok")).isEqualTo(true);
        assertThat((List<?>) guessed.get("warnings")).anySatisfy(w -> assertThat(String.valueOf(w))
                .contains("field 'hosts'").contains("inferred Data Splitter"));

        final Map<String, Object> given = world.call(tool, Map.of("mapping", mapping, "sample", sample,
                "splitter", Map.of("kind", "delimited", "header", true)));
        assertThat(given.get("ok")).isEqualTo(false);
        assertThat((List<?>) given.get("problems")).anySatisfy(p -> assertThat(String.valueOf(p))
                .contains("field 'hosts'"));
    }

    @Test
    void aMappingRefusedForItsShapeSaysWhatIsMissingAndShowsAWholeOne() {
        // Seen (a VS Code session): input left out, a rule without name, and an xpath run into its path's string
        // ('EventDetail/View/User/Id**,xpath:'), answered with "Field required" and "give exactly one of".
        final Map<String, Object> bad = Map.of("events", List.of(Map.of("name", "a"), Map.of("fields", List.of(
                Map.of("path", "EventDetail/View/User/Id**,xpath: \"User\"")))));

        final String text = String.valueOf(world.call(tool, Map.of("mapping", bad)).get("error"));

        assertThat(text).contains("input: Field required (What the XSLT reads: data_splitter")
                .contains("events[1].name: Field required (Short name for this kind of event, e.g. 'logon'.)")
                .contains("holds more than a path")
                .contains("{\"path\": \"EventDetail/View/User/Id\", \"xpath\": \"<the xpath>\"}")
                .contains(PyJson.dumps(MappingProblems.MAPPING_EXAMPLE));
        // A value problem alone gets no example: the shape is right.
        final Map<String, Object> value = new LinkedHashMap<>(MappingProblems.MAPPING_EXAMPLE);
        value.put("common", List.of(Map.of("path", "EventSource/System/Name", "field", "a", "any_of",
                List.of("b"))));
        final String said = String.valueOf(world.call(tool, Map.of("mapping", value)).get("error"));
        assertThat(said).contains("given field and any_of").contains("any_of alone")
                .doesNotContain("A whole mapping looks like");
    }

    @Test
    void theExampleMappingGenerates() {
        assertThat(XsltGenerator.generate(TranslationMapping.parse(MappingProblems.MAPPING_EXAMPLE),
                TranslationWorld.SCHEMA, TranslationWorld.VERSION).problems()).isEmpty();
    }

    // --- tests/test_build_feedback.py: what is wrong and where, before any slow work. ---

    @Test
    void textThatIsNotJsonSaysWhereAndHowBackslashesTravel() {
        final String message = String.valueOf(world.call(tool, Map.of("mapping",
                "{\"input\": \"json\", \"extract\": [{\"regex\": \"\\[\\[AppServer\"}]}")).get("error"));

        assertThat(message).contains("Invalid \\escape at character").contains("around `");
        assertThat(message.toLowerCase()).contains("only the json of your call doubles each backslash");
    }

    @Test
    void anInvalidMappingSaysWhereEachErrorIsWithTheValueGiven() {
        final Map<String, Object> bad = good();
        bad.put("rules", List.of());
        bad.put("events", List.of(Map.of("fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logon"))),
                Map.of("name", "x", "fields", "all of them")));

        final String message = String.valueOf(world.call(tool, Map.of("mapping", bad, "stream_ids", List.of(5)))
                .get("error"));

        assertThat(message).contains("events[0].name: Field required")
                .contains("events[1].fields: Input should be a valid list (given \"all of them\")")
                .contains("keys that are not part of a mapping: ['rules']");
    }

    @Test
    void schemaProblemsComeBackBeforeTheSampleIsRead() {
        final Map<String, Object> mapping = good();
        final List<Object> common = new ArrayList<>(list(mapping.get("common")));
        common.add(Map.of("path", "EventSource/Nope", "value", "x"));
        mapping.put("common", common);

        final Map<String, Object> result = world.call(tool, Map.of("mapping", mapping, "stream_ids", List.of(5)));

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("sample_check")).isEqualTo("not run: fix the problems first");
        // Without each rule's summary, so the problems come back whole (seen: 20-70 KB results, read in parts).
        assertThat(result).containsKey("problems").doesNotContainKey("events");
        Mockito.verifyNoInteractions(world.sampleStreamTexts);
    }

    @Test
    void thePipelineIsSteppedOnlyWhenTheFieldMappingPreviewIsAskedFor() {
        jsonStream();
        final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
        Mockito.when(pipeline.defaultOutputs()).thenReturn(List.of("translationFilter"));
        Mockito.when(world.stepping.load("p")).thenReturn(pipeline);
        Mockito.when(world.stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of(), false));

        final Map<String, Object> result = world.call(tool, Map.of("mapping", good(), "stream_ids", List.of(5),
                "pipeline_uuid", "p"));
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result).doesNotContainKey("field_mapping");
        assertThat((String) result.get("field_mapping_needs")).contains("write_documentation makes the section");
        Mockito.verify(world.stepping, Mockito.never()).outputs(any(), any(), any(), anyString(), any(), anyInt());

        world.call(tool, Map.of("mapping", good(), "stream_ids", List.of(5), "pipeline_uuid", "p", "field_mapping",
                true));
        // A preview: 50 records unless asked.
        Mockito.verify(world.stepping).outputs(any(), any(), any(), anyString(), any(), eq(50));
    }

    @Test
    void keysAMappingHasNoPlaceForAreReportedWhenItIsOtherwiseFine() {
        jsonStream();
        final Map<String, Object> mapping = good();
        mapping.put("extracts", List.of());

        final Map<String, Object> result = world.call(tool, Map.of("mapping", mapping, "stream_ids", List.of(5)));

        assertThat((List<?>) result.get("warnings")).anySatisfy(w -> assertThat(String.valueOf(w))
                .contains("mapping keys ignored, as a mapping has no such key: ['extracts']"));
    }

    // --- tests/test_changes_on_draft.py: changes= on a first save, applied to the session's draft. ---

    @Test
    void aFirstSaveAppliesChangesToTheDraft() {
        // Seen in testing: a Fortigate draft of 50 KB (60 extract entries, 10 rules with their data lists) had to
        // be sent whole in the first build_translation_xslt call; the agent couldn't write it out in one call, and
        // stopped twice to ask.
        final Map<String, Object> draft = world.call(world.draftTool(), Map.of("samples", Map.of("fw.jsonl",
                fortigate()), "source_name", "Fortigate", "build", "fg-v1"));
        assertThat((String) draft.get("hint")).contains("changes= only the rules and entries you change");

        final Map<String, Object> result = world.call(tool, Map.of("changes", Map.of("common", SYSTEM),
                "build", "fg-v1", "name", "FG-Events", "sample", fortigate()));

        assertThat(result.get("changes_applied_to")).isEqualTo("the draft draft_translation_mapping made");
        final Map<String, Object> sent = sent("fg-v1/FG-Events");
        assertThat(systemName(sent)).isEqualTo("FortiOS");
        // The rest of the draft came with it: its key/value pairs and rules, not sent again.
        final Map<String, Object> drafted = map(draft.get("mapping"));
        assertThat((List<?>) drafted.get("pairs")).isNotEmpty();
        assertThat(drafted).doesNotContainKey("extract");
        assertThat(sent.get("pairs")).isEqualTo(drafted.get("pairs"));
        assertThat(names(sent)).isEqualTo(names(drafted));
    }

    @Test
    void whatWasSentOrSavedComesBeforeTheDraft() {
        final Map<String, Object> saved = JsonUtil.readValue("{\"common\": [{\"path\": \"EventSource/System/Name\", "
                                                             + "\"value\": \"Saved\"}], \"input\": \"json\", "
                                                             + "\"json_layout\": \"lines\", \"events\": [{\"name\": "
                                                             + "\"all\", \"fields\": [{\"path\": "
                                                             + "\"EventDetail/TypeId\", \"value\": \"x\"}]}]}",
                Map.class);
        keptXslt("fg-v1", "FG-Events", saved, "4.1.0");
        world.call(world.draftTool(), Map.of("samples", Map.of("fw.jsonl", fortigate()), "build", "fg-v1"));

        // The build's XSLT of that name, saved (from another session, say): its mapping, not the draft.
        Map<String, Object> result = world.call(tool, Map.of("changes", Map.of("description", "kept"),
                "build", "fg-v1", "name", "FG-Events", "sample", fortigate()));
        assertThat(result).doesNotContainKey("changes_applied_to");
        assertThat(systemName(sent("fg-v1/FG-Events"))).isEqualTo("Saved");
        // A new name: the draft.
        result = world.call(tool, Map.of("changes", Map.of("common", SYSTEM), "build", "fg-v1", "name",
                "FG-Events-V2", "sample", fortigate()));
        assertThat(result).containsKey("changes_applied_to");
        assertThat(systemName(sent("fg-v1/FG-Events-V2"))).isEqualTo("FortiOS");
        // Sent since (the draft changed once): changes go to that, not the draft again.
        result = world.call(tool, Map.of("changes", Map.of("description", "again"), "build", "fg-v1", "name",
                "FG-Events-V2", "sample", fortigate()));
        assertThat(result).doesNotContainKey("changes_applied_to");
        assertThat(systemName(sent("fg-v1/FG-Events-V2"))).isEqualTo("FortiOS");
    }

    @Test
    void withNoDraftOrMappingTheRefusalSaysWhatToDo() {
        assertThat(world.call(tool, Map.of("changes", Map.of("common", SYSTEM), "build", "fg-v1", "name",
                "FG-Events", "sample", fortigate())).get("error")).asString()
                .contains("draft one, or give the whole mapping");
    }

    // --- tests/test_mapping_changes.py: a fix sends only what changes. ---

    @Test
    void aFixSendsOnlyTheRuleItChanges() {
        // Seen (Gemma 4 31B, VS Code): four failed builds, each resending the whole mapping at ~40 s of writing
        // apiece.
        final Map<String, Object> broken = TranslationWorld.mapping();
        final List<Object> events = new ArrayList<>(list(broken.get("events")));
        final Object good = events.getFirst();
        events.set(0, Map.of("name", "logon", "fields", List.of(Map.of("path", "EventDetail/Nope", "value", "x"))));
        broken.put("events", events);

        final Map<String, Object> failed = world.call(tool, Map.of("mapping", broken, "build", "acme-v1", "name",
                "ACME-Events"));
        assertThat(failed.get("ok")).isEqualTo(false);
        assertThat((String) failed.get("hint")).contains("changes=").contains("same build and name");

        final Map<String, Object> fixed = world.call(tool, Map.of("changes", Map.of("events", List.of(good)),
                "build", "acme-v1", "name", "ACME-Events", "include_xslt", false));
        assertThat(fixed.get("changes_applied")).isEqualTo(List.of("rule 'logon' replaced"));
        assertThat(fixed.get("problems")).isEqualTo(List.of());
        assertThat(fixed.get("ok")).isEqualTo(true);
        final String uuid = String.valueOf(map(fixed.get("saved")).get("uuid"));
        assertThat(keptMapping(uuid).events().stream().map(rule -> rule.name()).toList())
                .containsExactly("logon", "other");
        // And the next fix starts from what was saved, under its uuid.
        assertThat(SentMappings.sent(world.context(), uuid)).isPresent();
    }

    @Test
    void changesToASavedXsltStartFromTheMappingKeptWithIt() {
        // Another replica, or a new session: nothing remembered, so the mapping saved with the XSLT is changed.
        final String uuid = keptXslt("acme-v1", "ACME-Events", TranslationWorld.mapping(), null);

        final Map<String, Object> result = world.call(tool, Map.of("changes", Map.of("unmatched", "warn"),
                "uuid", uuid));

        assertThat(result.get("changes_applied")).isEqualTo(List.of("'unmatched' replaced"));
        assertThat(result).containsKey("saved");
        final TranslationMapping saved = keptMapping(uuid);
        assertThat(saved.unmatched()).isEqualTo("warn");
        assertThat(saved.events().stream().map(rule -> rule.name()).toList()).containsExactly("logon", "other");
    }

    @Test
    void changesNeedAMappingToChange() {
        assertThat(world.call(tool, Map.of("changes", Map.of("unmatched", "warn"), "build", "acme-v1", "name", "new"))
                .get("error")).asString().contains("None was found for those: draft one, or give the whole mapping");
        assertThat(world.call(tool, Map.of("mapping", TranslationWorld.mapping(), "changes", Map.of("unmatched",
                "warn"))).get("error")).asString().contains("not both");
        assertThat(world.call(tool, Map.of()).get("error")).asString().contains("Give mapping");
    }

    @Test
    void aSavedXsltIsRegeneratedFromItsKeptMappingWithoutSendingIt() {
        // Seen in production: asked to restyle an XSLT, an agent wrote a mapping from memory (refused), then sent
        // the 1,400-line kept mapping back whole. uuid alone regenerates it, as the generator writes it now.
        final String uuid = keptXslt("acme-v1", "ACME-Events", TranslationWorld.mapping(), "3.5.2");

        final Map<String, Object> result = world.call(tool, Map.of("uuid", uuid));

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result).containsKey("saved");
        // The version it was saved for, not the newest installed.
        Mockito.verify(world.heldSchemas, Mockito.atLeastOnce()).eventSchema("3.5.2");
        final XsltDoc xslt = world.xslt(uuid);
        assertThat(xslt.getDescription()).contains("Regenerated from its mapping");
        assertThat(keptMapping(uuid).events().stream().map(rule -> rule.name()).toList())
                .containsExactly("logon", "other");
        // The style is today's defaults: interpolated values, variables just in time.
        assertThat(xslt.getData()).contains("Value=\"{").doesNotContain("<xsl:attribute name=\"Value\"");
        // Without a uuid (or build and name) there is nothing to regenerate.
        assertThat(world.call(tool, Map.of()).get("error")).asString().contains("uuid= a saved XSLT alone");
    }

    // --- tests/test_authoring_guards.py and tests/test_draft.py. ---

    @Test
    void buildTranslationXsltTellsTheParserSetting() {
        final Map<String, Object> mapping = TranslationWorld.mapping();
        mapping.put("input", "json");
        mapping.put("json_layout", "lines");

        final Map<String, Object> result = world.call(tool, Map.of("mapping", mapping));

        assertThat(result.get("set_properties")).isEqualTo(List.of(Map.of("element", "jsonParser", "name",
                "addRootObject", "value", true)));
    }

    @Test
    void aFieldInventorySentAsTheMappingGetsTheDraftBack() {
        final List<Map<String, Object>> inventory = List.of(Map.of("field", "timestamp", "type", "timestamp"),
                Map.of("field", "device", "type", "string"), Map.of("field", "src_ip", "type", "ip"));

        final Map<String, Object> result = world.call(tool, Map.of("mapping", inventory, "sample", FW_JSON));

        assertThat(result.get("status")).isEqualTo("needs_mapping");
        assertThat(list(result.get("problems")).getFirst())
                .isEqualTo("mapping is a list of fields, not a translation mapping");
        assertThat(map(result.get("draft_mapping")).get("input")).isEqualTo("json");
        assertThat((String) result.get("hint")).contains("Edit draft_mapping");
        // The draft, sent back as the mapping, generates: every kind of this sample drafted with its action
        // element.
        final Map<String, Object> again = world.call(tool, Map.of("mapping", result.get("draft_mapping"),
                "sample", FW_JSON));
        assertThat(again.get("ok")).as(String.valueOf(again.get("problems"))).isEqualTo(true);
    }

    // The mapping of test_generation.py's confirmation case: logons, status lines kept as Unknown, the rest.
    private static Map<String, Object> keptAsUnknown() {
        final Map<String, Object> mapping = TranslationWorld.mapping();
        final List<Object> events = list(mapping.get("events"));
        mapping.put("events", List.of(events.get(0), JsonUtil.readValue("{\"name\": \"status\", \"when\": "
                                                                        + "[{\"field\": \"action\", \"equals\": "
                                                                        + "\"status\"}], \"allow_unknown\": "
                                                                        + "\"status lines carry no activity\", "
                                                                        + "\"fields\": [{\"path\": "
                                                                        + "\"EventDetail/TypeId\", \"value\": "
                                                                        + "\"status\"}, {\"path\": "
                                                                        + "\"EventDetail/Unknown/Data\", "
                                                                        + "\"data_name\": \"action\", \"field\": "
                                                                        + "\"action\"}]}", Map.class),
                events.get(1)));
        return mapping;
    }

    // test_sample_check_speed.py's SAMPLE: a Fortigate key=value body in JSON lines.
    private static String fortigate() {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            final Map<String, Object> pairs = new LinkedHashMap<>();
            pairs.put("logid", String.format("\"%010d\"", i));
            pairs.put("type", i % 3 != 0
                    ? "\"traffic\""
                    : "\"event\"");
            pairs.put("action", "\"accept\"");
            pairs.put("srcip", "10.0.0." + (i % 250));
            pairs.put("dstip", "8.8.8.8");
            pairs.put("user", "\"u" + i + "\"");
            pairs.put("msg", "\"message " + i + "\"");
            if (i % 7 == 0) {
                pairs.remove("user");
            }
            final List<String> body = new ArrayList<>();
            pairs.forEach((key, value) -> body.add(key + "=" + value));
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("time", "2026-10-01T10:00:00.000Z");
            record.put("host", "fw1");
            record.put("body", String.join(" ", body));
            lines.add(PyJson.dumps(record));
        }
        return String.join("\n", lines);
    }

    private void jsonStream() {
        Mockito.when(world.sampleStreamTexts.read(any())).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 5", "{\"ts\": \"2026-10-01T00:00:00.000Z\"}\n"), List.of()));
    }

    // An XSLT the tools saved in the build with its mapping kept.
    private String keptXslt(final String build,
                            final String name,
                            final Map<String, Object> mapping,
                            final String version) {
        final DocRef ref = world.inBuild(build, XsltDoc.TYPE, name);
        final Map<String, Object> payload = new LinkedHashMap<>();
        if (version != null) {
            payload.put("schema_version", version);
        }
        payload.put("mapping", mapping);
        world.world.docs.put(ref.getUuid(), world.xslt(ref.getUuid()).copy()
                .description(KeptMapping.withMapping("Acme events", "translation", payload)).build());
        return ref.getUuid();
    }

    private TranslationMapping keptMapping(final String uuid) {
        return TranslationMapping.parse(KeptMapping.readMapping(world.xslt(uuid).getDescription()).orElseThrow()
                .payload().get("mapping"));
    }

    private Map<String, Object> sent(final String target) {
        return SentMappings.sent(world.context(), target).orElseThrow();
    }

    private static Object systemName(final Map<String, Object> mapping) {
        return list(mapping.get("common")).stream()
                .map(TestBuildTranslationXsltTool::map)
                .filter(entry -> "EventSource/System/Name".equals(entry.get("path")))
                .map(entry -> entry.get("value"))
                .findFirst()
                .orElse(null);
    }

    private static List<Object> names(final Map<String, Object> mapping) {
        return list(mapping.get("events")).stream().map(rule -> map(rule).get("name")).toList();
    }

    private static Map<String, Object> good() {
        return JsonUtil.readValue(GOOD, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(final Object value) {
        return (List<Object>) value;
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
