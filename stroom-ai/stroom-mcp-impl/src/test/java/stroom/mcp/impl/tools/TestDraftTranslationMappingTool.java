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

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.documentation.shared.DocumentationDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// draft_translation_mapping, the tool (the draft itself is [stroom.ai.impl.tools.draft.DraftMapping]'s, tested with
/// it).
class TestDraftTranslationMappingTool {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n"
                                       + "2026-10-01T10:05:00Z,bob,logout\n";
    // A firewall's sample.
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
    void theUserChoosesHowMuchOfEachStackTraceIsKept() {
        // Asked for by the user: asked, the whole trace by default.
        final String traced = "2026-10-11 22:14:01.000 INFO [w1] Request 1 served\n"
                              + "user=bob action=logout session=s1\n"
                              + "2026-10-11 22:14:03.123 ERROR [w3] Request 3 failed\n"
                              + "java.lang.IllegalStateException: request 3 rejected\n"
                              + "\tat com.example.Service.handle(Service.java:10)\n"
                              + "\tat com.example.Main.main(Main.java:3)\n"
                              + "user=carol action=logout session=s2\n"
                              + "2026-10-11 22:14:04.456 INFO [w4] Request 4 served\n";

        // No forms: kept whole, the agent told to ask.
        final Map<String, Object> whole = world.call(tool, Map.of("samples", Map.of("app.log", traced)));
        assertThat(String.valueOf(whole.get("notes"))).contains("kept whole as Data 'continuation'",
                "continuation_lines=");
        assertThat(String.valueOf(whole.get("mapping"))).doesNotContain("position() le");

        // In a form: the user's choice.
        world.forms = true;
        world.answers.put(DraftTranslationMappingTool.TRACE_QUESTION, "Its first 20 lines");
        // Its key=value lines have no time: left for the agent to map here.
        world.answers.put(TimeSources.TIME_QUESTION, "Leave it: I'll say which field");
        world.answers.put(TimeSources.ZONE_QUESTION, "UTC");
        final Map<String, Object> cut = world.call(tool, Map.of("samples", Map.of("app.log", traced)));
        assertThat(world.asked).contains(DraftTranslationMappingTool.TRACE_QUESTION);
        assertThat(String.valueOf(cut.get("mapping"))).contains("[position() le 20]");
        assertThat(String.valueOf(cut.get("notes"))).contains("cut to its first 20 line(s)");

        // Given, nothing is asked.
        world.asked.clear();
        final Map<String, Object> given = world.call(tool, Map.of("samples", Map.of("app.log", traced),
                DraftTranslationMappingTool.CONTINUATION_LINES_ARG, 1));
        assertThat(world.asked).doesNotContain(DraftTranslationMappingTool.TRACE_QUESTION);
        assertThat(String.valueOf(given.get("mapping"))).contains("[position() le 1]");

        // No stack traces, no question.
        world.asked.clear();
        world.call(tool, Map.of("samples", Map.of("acme.csv", CSV)));
        assertThat(world.asked).doesNotContain(DraftTranslationMappingTool.TRACE_QUESTION);
    }

    @Test
    void theUserSaysWhereATimeComesFromWhenRecordsHaveNone() {
        // key=value lines with no time: the stream's received or created time is offered, never assumed.
        final String timeless = "user=bob action=logout session=s1\nuser=carol action=login session=s2\n"
                                + "user=dave action=logout session=s3\n";

        final Map<String, Object> asked = world.call(tool, Map.of("samples", Map.of("app.log", timeless)));
        assertThat(String.valueOf(asked.get("notes"))).contains("write no EventTime/TimeCreated", "time_source=");
        assertThat(String.valueOf(asked.get("mapping"))).doesNotContain("ReceivedTime");

        world.forms = true;
        world.answers.put(TimeSources.TIME_QUESTION, "When Stroom received the stream (an approximation)");
        final Map<String, Object> chosen = world.call(tool, Map.of("samples", Map.of("app.log", timeless)));
        // Every rule's, so once, in common.
        assertThat(String.valueOf(((Map<?, ?>) chosen.get("mapping")).get("common")))
                .contains("{path=EventTime/TimeCreated, meta=ReceivedTime}");
        assertThat(String.valueOf(chosen.get("notes"))).contains("an approximation");

        world.asked.clear();
        final Map<String, Object> given = world.call(tool, Map.of("samples", Map.of("app.log", timeless),
                TimeSources.TIME_SOURCE_ARG, "created"));
        assertThat(world.asked).doesNotContain(TimeSources.TIME_QUESTION);
        assertThat(String.valueOf(given.get("mapping"))).contains("meta=CreatedTime");
        assertThat(world.call(tool, Map.of("samples", Map.of("app.log", timeless), TimeSources.TIME_SOURCE_ARG,
                "now"))).containsEntry("error", "time_source is one of received, created, none");
    }

    @Test
    void theUserSaysWhereADateComesFromWhenTimesHaveNoneAndNoHeaderGivesIt() {
        // Lines holding a time of day only; the stream's file name holds the date.
        final String dateless = "23:59:30.000 INFO [w1] request 1 done\n00:00:10.000 WARN [w2] request 2 slow\n"
                                + "00:01:00.000 INFO [w1] request 3 done\n";
        Mockito.when(world.sampleStreamTexts.read(List.of(9L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 9", dateless), List.of()));
        Mockito.when(world.sampleStreamTexts.receiptHeaders(9L)).thenReturn(Map.of("RemoteFile",
                "app-2026-10-11.log"));

        final Map<String, Object> asked = world.call(tool, Map.of("stream_ids", List.of(9)));
        assertThat(String.valueOf(asked.get("notes"))).contains("have no date and no header gives it",
                "date_source=received|created|file_name");

        world.forms = true;
        world.answers.put(TimeSources.DATE_QUESTION, "The date in the file's name (e.g. app-2026-10-11.log)");
        world.answers.put(TimeSources.ZONE_QUESTION, "UTC");
        final Map<String, Object> chosen = world.call(tool, Map.of("stream_ids", List.of(9)));
        assertThat(String.valueOf(chosen.get("mapping")))
                .contains("replace(stroom:meta('RemoteFile'), '^.*?(\\d{4}-\\d{2}-\\d{2}).*$', '$1')")
                .contains("time_format=yyyy-MM-dd HH:mm:ss.SSS", "rollover=true");
        assertThat(world.asked).contains(TimeSources.DATE_QUESTION).doesNotContain(TimeSources.TIME_QUESTION);

        // No file name holding a date: not offered.
        Mockito.when(world.sampleStreamTexts.receiptHeaders(9L)).thenReturn(Map.of());
        assertThat(world.call(tool, Map.of("stream_ids", List.of(9), TimeSources.DATE_SOURCE_ARG, "file_name")))
                .containsEntry("error", "date_source is one of received, created, none");
        assertThat(String.valueOf(world.call(tool, Map.of("stream_ids", List.of(9), TimeSources.DATE_SOURCE_ARG,
                "received")).get("mapping"))).contains("substring(stroom:meta('ReceivedTime'), 1, 10)");
    }

    @Test
    void theUserSaysWhichZoneTimesWithNoneAreIn() {
        // Asked for by the user: a time with no zone or offset is never assumed to be UTC.
        final String local = "time,user,action\n2026-10-01 10:00:00,bob,login\n2026-10-01 10:04:00,carol,logout\n";
        Mockito.when(world.sampleStreamTexts.read(List.of(11L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 11", local), List.of()));
        // Received five minutes after its last event, in UTC: the source writes +10:00.
        Mockito.when(world.sampleStreamTexts.receiptHeaders(11L)).thenReturn(Map.of("ReceivedTime",
                "2026-10-01T00:09:00.000Z"));

        final Map<String, Object> asked = world.call(tool, Map.of("stream_ids", List.of(11)));
        assertThat(String.valueOf(asked.get("notes"))).contains("have no time zone or offset", "never assume UTC",
                "timezone=");
        assertThat(String.valueOf(asked.get("mapping"))).doesNotContain("timezone");

        world.forms = true;
        world.answers.put(TimeSources.ZONE_QUESTION, "+10:00 (a guess: the sample's latest time is about 10 hours "
                                                     + "ahead of when Stroom received it)");
        final Map<String, Object> chosen = world.call(tool, Map.of("stream_ids", List.of(11)));
        assertThat(String.valueOf(chosen.get("mapping"))).contains("timezone=+10:00");
        assertThat(String.valueOf(world.formsShown.getLast())).contains("UTC", "+10:00 (a guess",
                "Another zone: I'll name it");

        world.asked.clear();
        assertThat(String.valueOf(world.call(tool, Map.of("stream_ids", List.of(11), TimeSources.TIMEZONE_ARG,
                "Australia/Sydney")).get("mapping"))).contains("timezone=Australia/Sydney");
        assertThat(world.asked).doesNotContain(TimeSources.ZONE_QUESTION);
        assertThat(world.call(tool, Map.of("stream_ids", List.of(11), TimeSources.TIMEZONE_ARG, "Sydney")))
                .extractingByKey("error").asString().contains("is not a time zone");
    }

    @Test
    void eachRecordsZoneMayBeReadFromAField() {
        // Asked for by the user: a field naming each record's zone, read record by record rather than one zone.
        final String zoned = "time,tz,user,action\n2026-10-01 10:00:00,Australia/Sydney,bob,login\n"
                             + "2026-10-01 10:04:00,Europe/London,carol,logout\n";
        Mockito.when(world.sampleStreamTexts.read(List.of(12L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 12", zoned), List.of()));
        world.forms = true;
        world.answers.put(TimeSources.ZONE_QUESTION, "Each record's own zone, from 'tz' (UTC where a record gives "
                                                     + "none)");

        final Map<String, Object> chosen = world.call(tool, Map.of("stream_ids", List.of(12)));
        assertThat(String.valueOf(chosen.get("mapping"))).contains("timezone={field=tz, default=UTC}");
        assertThat(String.valueOf(chosen.get("notes"))).contains("each record's own zone", "never guess");

        // Given as the argument, with a default other than UTC.
        world.asked.clear();
        assertThat(String.valueOf(world.call(tool, Map.of("stream_ids", List.of(12), TimeSources.TIMEZONE_ARG,
                "field:tz|Australia/Sydney")).get("mapping"))).contains("timezone={field=tz, "
                                                                         + "default=Australia/Sydney}");
        assertThat(world.asked).doesNotContain(TimeSources.ZONE_QUESTION);
        assertThat(world.call(tool, Map.of("stream_ids", List.of(12), TimeSources.TIMEZONE_ARG, "field:tz|AEST")))
                .extractingByKey("error").asString().contains("a default Stroom reads");
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
        assertThat(ToolJson.loads(world.files().get("/outputs/mappings/draft-acme-v1.json")))
                .isEqualTo(ToolJson.loads(ToolJson.dumps(draft.get("mapping"))));
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
        // Seen in testing: V1.3 drafted for a feed V1.2 translates, and its IP lookups were gone, though the user
        // meant to keep them.
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
        assertThat(ToolJson.dumpsCompact(reply).length()).isLessThan(8000);
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

        assertThat(ToolJson.dumps(draft.get("mapping"))).contains("EventDetail/Authenticate");
    }

    @Test
    void theSystemTheUserGaveIsKeptForTheBuildAndAnotherIsRefused() {
        // From the chat 163 review: the user answered Test, and a later draft was given Prod.
        world.inBuild("acme-v1", DocumentationDoc.TYPE, "Acme source notes");
        final Map<String, Object> first = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1", "system_name", "Acme Gateway", "environment", "Test"));
        assertThat(ToolJson.dumps(first.get("mapping"))).contains("Acme Gateway").contains("Test");
        assertThat(world.world.records().system("acme-v1")).hasValueSatisfying(system -> assertThat(system)
                .containsEntry("system_name", "Acme Gateway")
                .containsEntry("environment", "Test"));

        final Map<String, Object> other = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1", "environment", "Prod"));
        assertThat((String) other.get("error")).contains("environment Test (not Prod)").contains("user_changed");

        final Map<String, Object> leftOut = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1"));
        assertThat(ToolJson.dumps(leftOut.get("mapping"))).contains("Acme Gateway").contains("Test")
                .doesNotContain("Prod");
    }

    @Test
    void aSystemTheUserChangedReplacesTheOneKept() {
        world.inBuild("acme-v1", DocumentationDoc.TYPE, "Acme source notes");
        world.call(tool, Map.of("samples", Map.of("acme.csv", CSV), "build", "acme-v1",
                "system_name", "Acme Gateway", "environment", "Test"));

        final Map<String, Object> changed = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1", "environment", "Prod", "user_changed", true));

        assertThat(ToolJson.dumps(changed.get("mapping"))).contains("Acme Gateway").contains("Prod");
        assertThat(world.world.records().system("acme-v1")).hasValueSatisfying(system -> assertThat(system)
                .containsEntry("environment", "Prod"));
    }

    @Test
    void classificationsStillToConfirmAreConfirmedBeforeTheDraft() {
        // Seen in chat 169: the agent drafted with eight kinds still to confirm, then searched its own history.
        final String notes = world.inBuild("acme-v1", DocumentationDoc.TYPE, "Acme source notes").getUuid();
        world.world.docs.put(notes, DocumentationDoc.builder().uuid(notes).name("Acme source notes").version("v0")
                .data("# Acme source notes\n\n<!-- stroom-mcp source notes\n{\"source\": \"Acme\", \"fields\": [], "
                      + "\"events\": [], \"proposed\": [{\"kind\": \"action=login\", \"event\": \"login\", "
                      + "\"description\": \"A user logged in\", \"field\": \"action\", \"value\": \"login\"}]}\n"
                      + "-->\n")
                .build());

        final Map<String, Object> refused = world.call(tool, Map.of("samples", Map.of("acme.csv", CSV),
                "build", "acme-v1"));

        assertThat((String) refused.get("error")).startsWith("1 classification(s) shown to the user are still to "
                                                             + "confirm").contains("confirm=true");
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
