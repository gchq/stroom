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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.sample.FeedSampling;
import stroom.ai.impl.tools.sample.RecordStream;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// survey_feed, the tool, and the progress it reports.
class TestSurveyFeedTool {

    private static final String JSON_LINES = "{\"a\": 1}\n{\"a\": 2}\n";

    private final BuildWorld world = new BuildWorld();
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final FeedNames feedNames = Mockito.mock(FeedNames.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final Consent consent = new Consent(() -> auditLog);
    private final SurveyFeedTool tool = new SurveyFeedTool(streamReader, feedNames, world.guard(),
            new SurveyDocuments(world.guard(), world.documentAccess, new DocumentWrites(world.documentAccess)), consent,
            new FewerStreams(securityContext),
            world.documentAccess, auditLog, besideFeed());

    TestSurveyFeedTool() {
        Mockito.when(world.documentAccess.canViewData()).thenReturn(true);
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(ResultPage.empty());
    }

    @Test
    void theValuesNamingTheKindOfEventAreCountedOverEveryShape() {
        // Seen: a 790 KB survey regrouped by x-type and x-action with jq, its shapes being their combinations.
        final List<Map<String, Object>> found = List.of(
                Map.of("signature", "row:x-type=nspolicy, x-action=allow", "count", 70L),
                Map.of("signature", "row:x-type=nspolicy, x-action=block", "count", 20L),
                Map.of("signature", "row:x-type=connection, x-action=allow", "count", 10L),
                Map.of("signature", "ts,user,event | event=login", "count", 5L),
                Map.of("signature", "kv:action=login, result=ok", "count", 3L),
                Map.of("signature", "text:sshd|Accepted password for <*>", "count", 9L),
                Map.of("signature", "cef:Acme|Door|1|100", "count", 4L));

        final Map<String, Map<String, Long>> kinds = SurveyFeedTool.kinds(found);

        assertThat(kinds.keySet()).containsExactly("x-type", "x-action", "event", "action", "result");
        assertThat(kinds.get("x-type")).containsExactly(Map.entry("nspolicy", 90L), Map.entry("connection", 10L));
        assertThat(kinds.get("x-action")).containsExactly(Map.entry("allow", 80L), Map.entry("block", 20L));
        assertThat(kinds.get("event")).containsExactly(Map.entry("login", 5L));
    }

    @Test
    void fewerStreamsThanTheDefaultAreTheUsersToConfirmOnce() {
        // Seen in VS Code: an agent surveyed a live feed 3 streams at a time until the user asked for a larger
        // sample. A number the user gives is still theirs: fewer is confirmed in a form, then not asked again for
        // that feed.
        feedHoldsMany();
        // Without a form, the user can't confirm it: nothing is read.
        assertThat(call("{\"feed\": \"ACME-VPN\", \"max_streams\": 3}", BuildToolCalls.context()).text())
                .startsWith("Not done: Survey feed 'ACME-VPN' only 3 stream(s) a call (the default is 10).");
        Mockito.verify(streamReader, Mockito.never()).readHead(anyLong(), anyInt(), anyInt());

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult agreed = call("{\"feed\": \"ACME-VPN\", \"max_streams\": 3}", user.context());
        assertThat(agreed.text()).isEqualTo("No Raw Events streams in feed 'ACME-VPN'");
        assertThat(user.shown()).singleElement().asString()
                .contains("only 3 stream(s) a call (the default is 10)");
        // Agreed: not asked again.
        assertThat(call("{\"feed\": \"ACME-VPN\", \"max_streams\": 3}", user.context()).text())
                .isEqualTo("No Raw Events streams in feed 'ACME-VPN'");
        assertThat(user.shown()).hasSize(1);
        call("{\"feed\": \"ACME-VPN\", \"max_streams\": 2}", user.context());
        assertThat(user.shown()).hasSize(2);
    }

    @Test
    void theDefaultOrALargerSampleIsNotAskedAbout() {
        for (final int count : List.of(10, 40)) {
            assertThat(call("{\"feed\": \"ACME-VPN\", \"max_streams\": " + count + "}").text())
                    .isEqualTo("No Raw Events streams in feed 'ACME-VPN'");
        }
    }

    @Test
    void aSamplesStreamsGivenByIdAreNotAskedAbout() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, JSON_LINES, false);

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1}"));

        assertThat(result.get("streams_read")).isEqualTo(List.of(7));
        assertThat(result.get("coverage")).isEqualTo("all 1 given stream(s) read whole: these are the kinds of event "
                                                     + "the sample holds");
    }

    @Test
    void aPartCutAtItsHeadIsReadInWindowsToItsEndTheirRecordsWhereTheyAre() {
        // Seen in testing: only the first 5,000,000 characters of a 160 MB part were surveyed, so the kinds of
        // event further in were found only once the feed was processed; and the coverage said to survey again at
        // the size just read.
        sampleFeedInABuild();
        final String header = "time,user,action";
        stream(7, "ACME-VPN", 0, header + "\n2026-10-01T10:00:00Z,alice,login\n2026-10-01T10:01:00Z,bob,login\n",
                true);
        Mockito.when(streamReader.readWindows(eq(7L), eq(0), anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                new StreamReader.Window("2026-10-01T12:00:00Z,carol,login\n", 5_001, 4_000_000),
                new StreamReader.Window("2026-10-01T23:00:00Z,dave,login\n2026-10-01T23:01:00Z,erin,useralert\n",
                        9_998, 8_000_000)));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"max_chars_per_stream\": 5000000}"));

        assertThat(result.get("records_read")).isEqualTo(5);
        assertThat((List<Map<String, Object>>) result.get("per_stream")).singleElement()
                .satisfies(stream -> assertThat(stream).containsEntry("windows", 2).containsEntry("head_only", true));
        // The kind only in the tail, at its own record (line 9,999 of the part, after the header).
        assertThat(String.valueOf(result.get("shapes"))).contains("useralert");
        assertThat((List<Map<String, Object>>) result.get("locations"))
                .anySatisfy(location -> assertThat(((Number) location.get("record")).longValue()).isEqualTo(9_998L));
        // Read in part, and the client shows no forms: the agent asks the user whether to read deeper.
        assertThat((String) result.get("coverage")).startsWith("NOT COVERED: 1 part(s) read only in part")
                .doesNotContain("max_chars_per_stream=5000000");
        assertThat((String) result.get("hint")).contains("ask the user in the chat whether to read these parts "
                                                         + "deeper", "read_deeper=");
    }

    @Test
    void quotedValuesOverSeveralLinesAreSaidToNeedQuotedNewlines() {
        // Seen in testing: the Data Splitter was built from the first lines, which held none, so processing split
        // each such record into pieces, the first translated with fields missing.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, "time,user,reason\n2026-10-01T10:00:00Z,alice,ok\n"
                                 + "2026-10-01T10:01:00Z,bob,\"Approved.\nTicket CHG-1\"\n"
                                 + "2026-10-01T10:02:00Z,carol,ok\n", false);
        stream(8, "ACME-VPN", 0, "time,user,reason\n2026-10-01T11:00:00Z,dave,ok\n", false);

        final Map<String, Object> result = json(call("{\"stream_ids\": [7, 8], \"max_streams\": 2}"));

        assertThat(result.get("records_read")).as("the record over two lines read as one").isEqualTo(4);
        assertThat((String) result.get("quoted_newlines")).startsWith("Some quoted values in streams [7] run over "
                                                                     + "several lines.")
                .contains("quoted_newlines=true");
        stream(9, "ACME-VPN", 0, "time,user,reason\n2026-10-01T12:00:00Z,erin,ok\n", false);
        assertThat(json(call("{\"stream_ids\": [9], \"max_streams\": 1}"))).doesNotContainKey("quoted_newlines");
    }

    @Test
    void aFewOfAnExistingFeedsStreamsByIdAreTheUsersToConfirm() {
        // Seen in testing: an existing feed onboarded from 5 streams, given by id, so not asked about.
        final DocRef existing = new DocRef(FeedDoc.TYPE, "feed-0", "ACME-VPN");
        Mockito.when(feedNames.feedsNamed("ACME-VPN")).thenReturn(List.of(existing));
        for (int id = 1; id <= 5; id++) {
            stream(id, "ACME-VPN", 0, "{\"a\": 1}\n", false);
        }
        feedHoldsMany();
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        call("{\"stream_ids\": [1, 2, 3, 4, 5]}", user.context());
        assertThat(user.shown()).singleElement().asString().contains("only 5 stream(s) a call");

        // A sample uploaded to a build's feed is read as it is.
        sampleFeedInABuild();
        assertThat(json(call("{\"stream_ids\": [1, 2, 3, 4, 5]}")).get("streams_read"))
                .isEqualTo(List.of(1, 2, 3, 4, 5));
    }

    @Test
    void aFeedOfNoMoreStreamsThanGivenIsNotAskedAbout() {
        // Seen in testing: a feed of one stream, and the user asked to agree to survey only that one, which was no
        // choice at all.
        final DocRef existing = new DocRef(FeedDoc.TYPE, "feed-0", "ACME-VPN");
        Mockito.when(feedNames.feedsNamed("ACME-VPN")).thenReturn(List.of(existing));
        final Meta only = stream(1, "ACME-VPN", 0, "{\"a\": 1}\n", false);
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean()))
                .thenReturn(new ResultPage<>(List.of(new MetaRow(only, null, Map.of()))));
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();

        assertThat(json(call("{\"stream_ids\": [1]}", user.context())).get("streams_read")).isEqualTo(List.of(1));
        assertThat(user.shown()).isEmpty();
    }

    @Test
    void aStreamNamedByIdIsSurveyedThroughEveryPart() {
        // Seen in testing: a feed's five latest streams uploaded again as one stream of five parts was surveyed
        // through three, so the kinds of event only in the last two were never seen.
        final Meta meta = meta(9, "ACME", 1_760_000_000_000L);
        Mockito.when(streamReader.meta(9L)).thenReturn(meta);
        Mockito.when(streamReader.readHead(eq(9L), anyInt(), anyInt())).thenAnswer(invocation ->
                new StreamReader.RawHead("{\"action\": \"a" + invocation.getArgument(1) + "\"}\n", false, 5));

        final Map<String, Object> result = json(call("{\"stream_ids\": [9]}"));

        for (int part = 0; part < 5; part++) {
            Mockito.verify(streamReader).readHead(eq(9L), eq(part), anyInt());
        }
        final Map<?, ?> read = (Map<?, ?>) ((List<?>) result.get("per_stream")).getFirst();
        assertThat(read.get("parts")).isEqualTo(5);
        assertThat(read.get("head_only")).isEqualTo(false);
    }

    @Test
    void aFeedsLastStreamsAreReadWholeAndOneReadOnlyInPartIsNotCovered() {
        // Seen in Ask Stroom AI: a one-stream feed read to its 334th record of 1,000 was "covered: every stream in
        // the feed has been read", saturated, and the agent found the other 666 records only through read_stream.
        final String records = IntStream.range(0, 3)
                .mapToObj(i -> "{\"action\": \"a" + i + "\"}\n")
                .collect(Collectors.joining());
        final Meta meta = stream(15911122, "SecretServer", 1_760_000_000_000L, records, true);
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean()))
                .thenReturn(new ResultPage<>(List.of(new MetaRow(meta, null, Map.of()))));

        final Map<String, Object> result = json(call("{\"feed\": \"SecretServer\"}"));

        // The feed's last stream: read whole where it can be.
        Mockito.verify(streamReader).readHead(15911122L, 0, FeedSampling.SAMPLE_CHARS);
        assertThat(result.get("saturated")).isEqualTo(false);
        assertThat(((Map<?, ?>) ((List<?>) result.get("per_stream")).getFirst()).get("head_only")).isEqualTo(true);
        assertThat(String.valueOf(result.get("coverage")))
                .startsWith("NOT COVERED: 1 part(s) read only in part: [stream 15911122 part 1");

        stream(15911122, "SecretServer", 1_760_000_000_000L, records, false);
        final Map<String, Object> whole = json(call("{\"feed\": \"SecretServer\"}"));
        assertThat(whole.get("saturated")).isEqualTo(true);
        assertThat(whole.get("coverage")).isEqualTo("covered: every stream in the feed has been read whole");
    }

    @Test
    void aPartReadOnlyInPartIsReadDeeperAsTheUserSaysFindingKindsFurtherIn() {
        // Seen in testing: a 25,000-record stream surveyed to its 10,000th record, its two rarest kinds, near its
        // end, never seen nor classified. The user is asked whether to read deeper, and how far.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, logins(10), true);
        Mockito.when(streamReader.partSize(7L, 0)).thenReturn(30_000L);
        // The first 8 read in order (the windows a quarter of the 10); read on from the 9th.
        Mockito.when(streamReader.rawRecords(eq(7L), eq(0), any(), anyBoolean(), eq(8L), anyInt(), anyInt()))
                .thenReturn(new RecordStream.Read(null, List.of(
                        "time=2026-10-01T11:00:00Z action=login user=u4",
                        "time=2026-10-01T23:59:00Z action=csrf_block user=u5"), 10, true, false, false));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Read to the end"));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"max_records_per_stream\": 10, \"build\": \"acme-v1\"}",
                user.context()));

        // Asked in a short form, how far each part was read and the choices.
        assertThat(user.shown()).singleElement().asString().startsWith("Survey of feed 'ACME-VPN': 1 part(s) read "
                                                                      + "only in part.")
                .contains("Stream 7: 8 of about");
        // Read on from where it stopped: the rare kind further in found; the part read whole, so covered.
        assertThat(String.valueOf(result.get("shapes"))).contains("csrf_block");
        assertThat(result.get("records_read")).isEqualTo(10);
        assertThat((List<Map<String, Object>>) result.get(SurveyDepth.DEPTH)).singleElement().satisfies(part ->
                assertThat(part).containsEntry("whole", true).containsEntry("records_read", 10).containsEntry(
                        "choice", "-1"));
        assertThat(result.get("saturated")).isEqualTo(true);
        assertThat(result.get("coverage")).isEqualTo("covered: every part read to its end");
    }

    @Test
    void theUserChoosingToStopSettlesTheCoverageAndIsntAskedAgain() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, logins(10), true);
        Mockito.when(streamReader.partSize(7L, 0)).thenReturn(40_000L);
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", SurveyDepth.STOP_HERE));
        final String arguments = "{\"stream_ids\": [7], \"max_streams\": 1, \"max_records_per_stream\": 10, "
                                 + "\"build\": \"acme-v1\"}";

        final Map<String, Object> result = json(call(arguments, user.context()));

        assertThat(result.get("saturated")).isEqualTo(true);
        assertThat((String) result.get("coverage")).startsWith("covered as far as the user chose: [stream 7 part 1 "
                                                               + "read to 8 of about")
                .endsWith("they chose to stop there");
        Mockito.verify(streamReader, Mockito.never()).rawRecords(anyLong(), anyInt(), any(), anyBoolean(),
                anyLong(), anyInt(), anyInt());
        // Kept in the build's survey doc: surveyed again, not asked again.
        json(call(arguments, user.context()));
        assertThat(user.shown()).hasSize(1);
    }

    @Test
    void withoutFormsTheUsersAnswerFromTheChatIsGivenAsReadDeeper() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, logins(10), true);
        Mockito.when(streamReader.rawRecords(eq(7L), eq(0), any(), anyBoolean(), eq(8L), anyInt(), anyInt()))
                .thenReturn(new RecordStream.Read(null, List.of("time=2026-10-01T11:00:00Z action=purge user=u2"), 9,
                        true, false, false));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"max_records_per_stream\": 10, \"read_deeper\": \"another "
                                                     + "5,000\"}"));

        assertThat(String.valueOf(result.get("shapes"))).contains("purge");
        assertThat((List<Map<String, Object>>) result.get(SurveyDepth.DEPTH)).singleElement().satisfies(part ->
                assertThat(part).containsEntry("choice", "5008").containsEntry("records_read", 9));
    }

    @Test
    void eachPartOfAStreamHasItsOwnBudget() {
        // Asked for by the user: a source can write different data to different parts, so a budget shared by the
        // parts could miss kinds of event in the later ones.
        final Meta meta = meta(9, "ACME-VPN", 0);
        Mockito.when(streamReader.meta(9L)).thenReturn(meta);
        Mockito.when(streamReader.readHead(eq(9L), eq(0), anyInt())).thenReturn(new StreamReader.RawHead(
                logins(8), false, 2));
        Mockito.when(streamReader.readHead(eq(9L), eq(1), anyInt())).thenReturn(new StreamReader.RawHead(
                logins(8).replace("action=login", "action=export"), false, 2));

        final Map<String, Object> result = json(call("{\"stream_ids\": [9], \"max_streams\": 1, "
                                                     + "\"max_records_per_stream\": 10}"));

        // Each part's eight records read: shared, the second part would have had only two.
        assertThat(result.get("records_read")).isEqualTo(16);
        assertThat(String.valueOf(result.get("shapes"))).contains("export");
        assertThat(result.get("coverage")).isEqualTo("all 1 given stream(s) read whole: these are the kinds of "
                                                     + "event the sample holds");
    }

    @Test
    void readingTheFeedsStreamsSaysSo() {
        // Seen: 28 stream searches, 2.4 s each, most of a 35 s survey_feed call.
        final Meta meta = stream(6, "ACME", 1_700_000_000_000L, JSON_LINES, false);
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean()))
                .thenReturn(new ResultPage<>(List.of(new MetaRow(meta, null, Map.of()))));
        final List<String> said = new ArrayList<>();
        final ToolCallContext context = ToolCallContext.builder(session())
                .userQuestions(UserQuestions.NONE)
                .progress(said::add)
                .build();

        assertThat(json(call("{\"feed\": \"ACME\"}", context)).get("streams_read")).isEqualTo(List.of(6));

        assertThat(said.getFirst()).isEqualTo("Finding up to 10 streams spread over the feed's time range");
        assertThat(said).contains("Reading the head of 1 stream of ACME");
    }

    @Test
    void shapesAndTheirLocationsAreGivenAndKeptInTheBuildsSurveyDoc() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 1_700_000_000_000L, "{\"user\": \"a\", \"action\": \"login\"}\n"
                                                  + "{\"user\": \"b\", \"action\": \"login\"}\n"
                                                  + "{\"user\": \"a\", \"action\": \"logout\"}\n", false);

        final Map<String, Object> first = json(call("{\"stream_ids\": [7], \"build\": \"acme-v1\"}"));

        assertThat(first.get("format")).isEqualTo("json lines");
        assertThat(first.get("records_read")).isEqualTo(3);
        final List<?> shapes = (List<?>) first.get("shapes");
        assertThat(shapes).hasSize(2);
        final Map<?, ?> login = (Map<?, ?>) shapes.getFirst();
        assertThat(login.get("count")).isEqualTo(2);
        assertThat(login.get("share_percent")).isEqualTo(66.7);
        assertThat(login.get("new")).isEqualTo(true);
        // The kind's other record too, its values differing (work package E9).
        assertThat((List<Object>) login.get("more_examples")).hasSize(1).doesNotContain(login.get("example"));
        assertThat((List<?>) first.get("locations")).hasSize(3);
        final Map<?, ?> doc = (Map<?, ?>) first.get("survey_doc");
        assertThat(doc.get("name")).isEqualTo("ACME-VPN - Survey");
        assertThat(doc.get("kinds_of_event")).isEqualTo(2);
        final Map<String, Object> state = SurveyDoc.readState(world.body(String.valueOf(doc.get("uuid"))));
        assertThat(state.get("streams_read")).isEqualTo(List.of(7L));

        // Continued from the doc: its shapes are known, so only new ones are to step.
        final Map<String, Object> again = json(call("{\"stream_ids\": [7], \"build\": \"acme-v1\"}"));
        assertThat(again.get("new_shapes")).isEqualTo(0);
        assertThat((List<?>) again.get("locations")).isEmpty();
        assertThat(((Map<?, ?>) again.get("survey_doc")).get("uuid")).isEqualTo(doc.get("uuid"));
        assertThat(world.created.stream().filter(made -> made.startsWith(DocumentationDoc.TYPE))).hasSize(1);
    }

    @Test
    void shapesSeenOnlyInStreamsDeletedSinceAreSurveyedAgainWithLocationsInTheStreamsThereAreNow() {
        // Seen in testing: a feed's streams deleted part-way through a build and its latest uploaded again; the
        // survey took the shapes it knew as known, so they had locations only in the deleted streams and couldn't
        // be stepped.
        sampleFeedInABuild();
        final String records = "{\"user\": \"a\", \"action\": \"login\"}\n"
                               + "{\"user\": \"b\", \"action\": \"login\"}\n"
                               + "{\"user\": \"a\", \"action\": \"logout\"}\n";
        stream(7, "ACME-VPN", 1_700_000_000_000L, records, false);
        json(call("{\"stream_ids\": [7], \"build\": \"acme-v1\"}"));
        // Stream 7 deleted; the same records uploaded again as stream 8.
        Mockito.when(streamReader.meta(7L)).thenReturn(meta(7, "ACME-VPN", 1_700_000_000_000L).copy()
                .status(Status.DELETED).build());
        stream(8, "ACME-VPN", 1_700_000_100_000L, records, false);

        final Map<String, Object> again = json(call("{\"stream_ids\": [8], \"build\": \"acme-v1\"}"));

        assertThat(again.get("gone_streams")).isEqualTo(List.of(7));
        assertThat(String.valueOf(again.get("gone_note"))).contains("2 shape(s) seen only in them are surveyed again");
        assertThat(again.get("new_shapes")).isEqualTo(2);
        assertThat((List<?>) again.get("locations")).hasSize(3)
                .allSatisfy(location -> assertThat(((Map<?, ?>) location).get("stream")).isEqualTo(8));
        final Map<String, Object> state = SurveyDoc.readState(world.body(String.valueOf(
                ((Map<?, ?>) again.get("survey_doc")).get("uuid"))));
        assertThat(state.get("streams_read")).isEqualTo(List.of(8L));
    }

    @Test
    void everyStreamReadSaysTheSurveyIsComplete() {
        final Map<String, Object> result = json(call("{\"feed\": \"ACME\", \"skip_stream_ids\": [1, 2]}"));

        assertThat(result.get("saturated")).isEqualTo(true);
        assertThat(result.get("hint")).isEqualTo("Every stream in the feed has been read: the survey is complete.");
    }

    @Test
    void aStreamReadOnlyInPartKeepsTheSurveyFromBeingCompleteUntilTheUserSettlesIt() {
        // Seen in testing: a stream read to its 10,000th record of many more, then a survey with no streams left to
        // pick said it was complete, and kinds only further in were found at processing.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, logins(10), true);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"max_records_per_stream\": 10, "
                  + "\"build\": \"acme-v1\"}"));

        final Map<String, Object> left = json(call("{\"feed\": \"ACME-VPN\", \"build\": \"acme-v1\"}"));

        assertThat(left.get("saturated")).isEqualTo(false);
        assertThat(left.get("coverage")).isEqualTo("NOT COVERED: stream(s) [7] read only in part");
        assertThat((String) left.get("hint")).contains("[7] only in part", "stream_ids [7]");

        // The user chooses to stop there: settled, so the survey is complete.
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"max_records_per_stream\": 10, "
                  + "\"build\": \"acme-v1\"}", BuildToolCalls.user(Map.of("answer", SurveyDepth.STOP_HERE))
                .context()));
        final Map<String, Object> settled = json(call("{\"feed\": \"ACME-VPN\", \"build\": \"acme-v1\"}"));
        assertThat(settled.get("saturated")).isEqualTo(true);
        assertThat(settled.get("hint")).isEqualTo("Every stream in the feed has been read: the survey is "
                                                  + "complete.");
    }

    @Test
    void linesOfMixedFormatsReadOnlyInPartAreOfferedDeeper() {
        // Seen in testing: a NetScaler stream of mixed lines read to its 10,000th record was never offered deeper.
        sampleFeedInABuild();
        final String mixed = IntStream.range(0, 4).mapToObj(i -> String.join("\n",
                        "<13>Oct 11 22:14:1%d host1 app[42]: user=alice action=login".formatted(i),
                        "user=bob action=logout src=10.0.0.%d".formatted(i),
                        "2026-10-11 22:14:1%d ERROR Something failed".formatted(i)))
                .collect(Collectors.joining("\n")) + "\n";
        stream(7, "ACME-VPN", 0, mixed, true);
        Mockito.when(streamReader.readWindows(eq(7L), eq(0), anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                new StreamReader.Window("2026-10-11 23:00:00 WARN Disk nearly full\n", 5_001, 4_000_000)));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"max_records_per_stream\": 10}"));

        assertThat(result.get("format")).isEqualTo(SampleProfiler.MIXED_LINES);
        // A window of one format's lines still read, as lines of the part.
        assertThat(String.valueOf(result.get("shapes"))).contains("Disk nearly full");
        assertThat((String) result.get("coverage")).startsWith("NOT COVERED: 1 part(s) read only in part");
        assertThat((String) result.get("hint")).contains("read_deeper=");
    }

    @Test
    void syslogReadOnlyInPartIsOfferedDeeper() {
        // Seen in testing (2026-11-12): a mail gateway's 30,000-line syslog streams could only be stopped at, "can't
        // be read further in a call", as syslog wasn't read from part-way through.
        sampleFeedInABuild();
        final String syslog = IntStream.range(0, 12).mapToObj(i -> ("Nov  9 00:02:%02d svr01.example.com msw "
                                                                     + "2026-11-09 00:01:%02d.240795 [1080890] "
                                                                     + "[required] [SEGTrap] id=\"q%d\"; "
                                                                     + "subject=\"Test\"").formatted(i, i, i))
                .collect(Collectors.joining("\n")) + "\n";
        stream(7, "ACME-VPN", 0, syslog, true);
        Mockito.when(streamReader.readWindows(eq(7L), eq(0), anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                new StreamReader.Window("Nov  9 09:00:00 svr01.example.com msw 2026-11-09 08:59:00.1 [1] [info] "
                                        + "[SEGTLS] peer=\"mx1\"\n", 5_001, 4_000_000)));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"max_records_per_stream\": 10}"));

        assertThat(String.valueOf(result.get("format"))).startsWith("syslog");
        assertThat(String.valueOf(result.get("shapes"))).contains("SEGTLS");
        assertThat((String) result.get("coverage")).startsWith("NOT COVERED: 1 part(s) read only in part");
        assertThat((String) result.get("hint")).contains("read_deeper=");
    }

    @Test
    void aStreamStroomCantReadIsSaidAndTheOthersAreStillSurveyed() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, JSON_LINES, false);
        Mockito.when(streamReader.meta(8)).thenReturn(meta(8, "ACME-VPN", 0));
        Mockito.when(streamReader.readHead(eq(8L), anyInt(), anyInt()))
                .thenThrow(new StreamReader.StreamReadException("Stroom could not read stream 8: volume gone"));

        final Map<String, Object> result = json(call("{\"stream_ids\": [7, 8]}"));

        assertThat(result.get("streams_read")).isEqualTo(List.of(7, 8));
        assertThat(String.valueOf(result.get("per_stream")))
                .contains("Stroom couldn't read it: Stroom could not read stream 8: volume gone");
    }

    @Test
    void theCallsTimeLimitStopsTheSurveyWithWhatItHasRead() {
        sampleFeedInABuild();
        for (int id = 1; id <= 3; id++) {
            stream(id, "ACME-VPN", 0, JSON_LINES, false);
        }
        Mockito.when(streamReader.readHead(anyLong(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Thread.sleep(80);
            return new StreamReader.RawHead(JSON_LINES, false, 1);
        });
        final ToolCallContext context = ToolCallContext.builder(session())
                .userQuestions(UserQuestions.NONE)
                .timeLimit(Duration.ofMillis(50))
                .build();

        final Map<String, Object> result = json(call("{\"stream_ids\": [1, 2, 3]}", context));

        assertThat(result.get("streams_read")).isEqualTo(List.of(1));
        assertThat(result.get("saturated")).isEqualTo(false);
        assertThat(String.valueOf(result.get("coverage"))).startsWith("NOT COVERED YET: this session's time limit "
                                                                      + "for a call (50 ms) stopped the survey after "
                                                                      + "1 of the 3 streams picked");
    }

    @Test
    void anInterruptedCallStopsAtOnce() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, JSON_LINES, false);
        Thread.currentThread().interrupt();
        final ToolResult result;
        try {
            result = call("{\"stream_ids\": [7]}");
        } finally {
            Thread.interrupted();
        }

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).isEqualTo("The survey was stopped: the call was cancelled.");
        Mockito.verify(streamReader, Mockito.never()).readHead(anyLong(), anyInt(), anyInt());
    }

    @Test
    void badArgumentsAreRefusedSayingWhy() {
        assertThat(call("{}").text()).isEqualTo("Give feed, or stream_ids");
        assertThat(call("{\"feed\": \"ACME\", \"max_streams\": 0}").text())
                .isEqualTo("max_streams must be a whole number from 1 to 100, not '0'");
        Mockito.when(world.documentAccess.canViewData()).thenReturn(false);
        assertThat(call("{\"feed\": \"ACME\"}").text()).contains("permission to view data");
    }

    @Test
    void itOnlyReadsWithoutABuildAndIsHeavy() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isReadOnly(BuildToolCalls.call(SurveyFeedTool.NAME, "{\"feed\": \"ACME\"}"))).isTrue();
        assertThat(tool.isReadOnly(BuildToolCalls.call(SurveyFeedTool.NAME, "{\"feed\": \"ACME\", \"build\": "
                                                                             + "\"b\"}"))).isFalse();
        assertThat(tool.hasReadOnlyCalls()).isTrue();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.usesRunSlot()).isTrue();
        assertThat(tool.category()).isEqualTo(AiToolCategory.PROCESSING);
    }

    // A feed holding more streams than a call asks for: as many as asked for, but none to pick for a survey (whose
    // first find is of one, the newest).
    @Test
    void anAgreementToFewerStreamsIsKeptWithTheBuildsSurvey() {
        // Asked for by the user: kept in node memory alone, the agreement was lost on a restart (and after four
        // hours), and the user asked again.
        // An existing feed, outside the build, three of its many streams named.
        world.inBuild("acme-v1", FeedDoc.TYPE, "ACME-VPN-TEST", "feed-2");
        feedHoldsMany();
        for (final long id : List.of(7L, 8L, 9L)) {
            stream(id, "ACME-VPN", id, logins(3), false);
        }
        final String args = "{\"stream_ids\": [7, 8, 9], \"build\": \"acme-v1\"}";
        // The survey of an existing feed, covered, asks where its doc goes at promotion too.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true, "answer",
                SurveyFeedTool.KEEP_IN_BUILD));
        assertThat(json(call(args, user.context()))).containsEntry(SurveyDoc.FEWER_STREAMS, 3);

        // A node restarted (its memory empty), with no form to ask in: the build's survey record still says so.
        final SurveyFeedTool restarted = new SurveyFeedTool(streamReader, feedNames, world.guard(),
                new SurveyDocuments(world.guard(), world.documentAccess, new DocumentWrites(world.documentAccess)),
                consent, new FewerStreams(securityContext), world.documentAccess, auditLog, besideFeed());
        final ToolResult again = restarted.execute(BuildToolCalls.call(SurveyFeedTool.NAME, args),
                BuildToolCalls.context());
        assertThat(again.text()).doesNotContain("Not done: Survey feed 'ACME-VPN' only 3 stream(s)");
        assertThat(json(again)).containsEntry(SurveyDoc.FEWER_STREAMS, 3);
    }

    // Nothing is beside any feed.
    private static SourceTypeRegistries besideFeed() {
        final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
        Mockito.when(registries.promotedSurvey(anyString())).thenReturn(Optional.empty());
        Mockito.when(registries.registry(any(), anyString())).thenReturn(Optional.empty());
        return registries;
    }

    private void feedHoldsMany() {
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenAnswer(find -> {
            final int limit = find.getArgument(1);
            return limit < 2
                    ? ResultPage.empty()
                    : new ResultPage<>(IntStream.range(0, limit)
                            .mapToObj(n -> new MetaRow(meta(100 + n, "ACME-VPN", 0), null, Map.of()))
                            .toList());
        });
    }

    @Test
    void aKindFieldNamedLikeNoneIsPutToTheUserAndKeptForTheSurvey() {
        // Seen in testing: a CSV mail log's kind in msg_act, a name the survey didn't know, its kind columns chosen
        // afresh in each part, and the survey never covered. The user says which field; the survey keeps it.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, msgActCsv(), false);

        // Without forms: nothing surveyed, the agent asks the user in the chat.
        final Map<String, Object> asking = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\"}"));
        assertThat(asking).containsEntry("status", "needs_kind_field").doesNotContainKey("streams_read");
        assertThat((List<Map<String, Object>>) asking.get("candidates")).extracting(candidate -> candidate.get("field"))
                .contains("msg_act", "direction").doesNotContain("timestamp", "sender", "subject");

        // In a form: the user picks it, and every record is signed by it.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer",
                "msg_act (allow, quarantine, reject)"));
        final Map<String, Object> chosen = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\"}", user.context()));
        assertThat(user.shown()).singleElement().asString().contains("msg_act (allow, quarantine, reject)");
        assertThat(chosen.get(SurveyDoc.KIND_FIELDS)).isEqualTo(Map.of("fields", List.of("msg_act"), "by", "user"));
        assertThat(String.valueOf(chosen.get("shapes"))).contains("row:msg_act=allow", "row:msg_act=reject")
                .doesNotContain("direction=");

        // Kept: the next call asks nothing.
        final BuildToolCalls.FormUser again = BuildToolCalls.user(Map.of("answer", "unused"));
        final Map<String, Object> next = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                   + "\"build\": \"acme-v1\"}", again.context()));
        assertThat(again.shown()).isEmpty();
        assertThat(next.get(SurveyDoc.KIND_FIELDS)).isEqualTo(Map.of("fields", List.of("msg_act"), "by", "user"));
    }

    @Test
    void anAggregatedFeedsSourceTypeFieldIsPutToTheUserAndItsSourceTypesCounted() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, wrappedLines(60), false);

        // Without forms: nothing surveyed, the agent asks the user in the chat.
        final Map<String, Object> asking = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\"}"));
        assertThat(asking).containsEntry("status", "needs_source_type_field").doesNotContainKey("streams_read");
        assertThat((List<Map<String, Object>>) asking.get("candidates")).extracting(candidate -> candidate.get("field"))
                .containsExactly("source");

        // In a form: the user confirms it; the kinds are told apart within the source types, and each is counted.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "source"));
        final Map<String, Object> chosen = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\"}", user.context()));
        assertThat(user.shown()).first().asString().contains("source (", "aggregates several sources");
        final Map<String, Object> sources = (Map<String, Object>) chosen.get("source_types");
        assertThat(sources).containsEntry("field", "source").containsEntry("by", "user");
        assertThat((Map<String, Object>) sources.get("records_read_by_value")).containsEntry("Security", 20)
                .containsEntry("Database", 20).containsEntry("Integration", 20);
        assertThat(String.valueOf(sources.get("registry"))).contains("record_source_types");

        // Kept: the next call asks nothing, and counts on.
        final BuildToolCalls.FormUser again = BuildToolCalls.user(Map.of("answer", "unused"));
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\"}", again.context()));
        assertThat(again.shown()).isEmpty();
    }

    @Test
    void theUserMaySayTheFeedIsOneSource() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, wrappedLines(60), false);

        final Map<String, Object> said = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                   + "\"build\": \"acme-v1\", \"source_type_field\": \"none\"}"));
        assertThat((Map<String, Object>) said.get("source_types")).containsEntry("aggregated", false);
        // Kept: never asked.
        final BuildToolCalls.FormUser again = BuildToolCalls.user(Map.of("answer", "unused"));
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\"}", again.context()));
        assertThat(again.shown()).isEmpty();
        assertThat(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                        + "\"source_type_field\": \"verdict\"}").text()).contains("names no field of the records");
    }

    @Test
    void aBuildsScopeIsSurveyedAloneTheOtherSourceTypesOnlyCounted() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, wrappedLines(60), false);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                  + "\"source_type_field\": \"source\"}"));
        scope(List.of("Security"));
        stream(8, "ACME-VPN", 1, wrappedLines(60), false);

        final Map<String, Object> scoped = json(call("{\"stream_ids\": [8], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\"}"));

        // Only the scope's records make shapes and count as read; the others are counted by source type only.
        assertThat(scoped).containsEntry("records_read", 20);
        assertThat(String.valueOf(scoped.get("shapes"))).contains("Security").doesNotContain("Database",
                "Integration");
        final Map<String, Object> scope = (Map<String, Object>) scoped.get("scope");
        assertThat(scope).containsEntry("records_passed_over", 40).containsEntry("source_types",
                List.of("Security"));
        assertThat(((Number) scope.get("share_percent")).doubleValue()).isEqualTo(33.3);
        assertThat((Map<String, Object>) ((Map<String, Object>) scoped.get("source_types"))
                .get("records_read_by_value")).containsEntry("Database", 20).containsEntry("Integration", 20);
        assertThat((String) scoped.get("coverage")).contains("within the scope: build 'acme-v1' covers Security");
        assertThat(SurveyDoc.readFor(surveyState(), 8L)).containsExactly("Security");
        assertThat(SurveyDoc.readFor(surveyState(), 7L)).containsExactly(SourceTypeScope.ALL);
    }

    @Test
    void aScopeThatAddsSourceTypesReadsTheStreamsAgainForThemAlone() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, wrappedLines(60), false);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                  + "\"source_type_field\": \"source\"}"));
        scope(List.of("Security"));
        stream(8, "ACME-VPN", 1, wrappedLines(60), false);
        json(call("{\"stream_ids\": [8], \"max_streams\": 1, \"build\": \"acme-v1\"}"));
        scope(List.of("Security", "Database"));

        final Map<String, Object> wider = json(call("{\"stream_ids\": [8], \"max_streams\": 1, "
                                                    + "\"build\": \"acme-v1\"}"));

        // Read again for the source type it wasn't read for: its Security records aren't counted twice.
        assertThat(wider).containsEntry("records_read", 20).containsKey("read_again");
        assertThat(String.valueOf(wider.get("shapes"))).contains("Database").doesNotContain("Integration");
        assertThat(SurveyDoc.readFor(surveyState(), 8L)).containsExactly("Database", "Security");
    }

    @Test
    void aScopesBudgetIsOfItsOwnRecords() {
        // A scope of a third of the records, read to 10 records a stream: 10 of the scope's, not 10 records of
        // which four are the scope's.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, wrappedLines(60), false);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                  + "\"source_type_field\": \"source\"}"));
        scope(List.of("Security"));
        stream(9, "ACME-VPN", 2, wrappedLines(60), false);

        final Map<String, Object> scoped = json(call("{\"stream_ids\": [9], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\", \"max_records_per_stream\": 10}"));

        assertThat(scoped).containsEntry("records_read", 10).doesNotContainKey("read_again");
        assertThat((Map<String, Object>) scoped.get("scope")).containsEntry("records_passed_over", 18);
    }

    // The scope the build's source types step records: these source types, matched by their own names.
    @SuppressWarnings("unchecked")
    private void scope(final List<String> names) {
        final SurveyDocuments docs = new SurveyDocuments(world.guard(), world.documentAccess,
                new DocumentWrites(world.documentAccess));
        final FeedSampling.SurveyRecord record = docs.load("acme-v1", "ACME-VPN").orElseThrow();
        final Map<String, Object> state = SurveyDoc.readState(record.text());
        final Map<String, Object> sources = new LinkedHashMap<>((Map<String, Object>) state.get("source_types"));
        sources.put("scope", Map.of("build", "acme-v1", "source_types", names, "rows", SourceTypeScope.kept(
                List.of("Security", "Database", "Integration").stream()
                        .map(name -> new SourceTypeRows.Row(name, SourceTypeRows.VALUE, name))
                        .toList())));
        state.put("source_types", sources);
        docs.write(session(), record, SurveyDoc.render(state));
    }

    private Map<String, Object> surveyState() {
        return SurveyDoc.readState(new SurveyDocuments(world.guard(), world.documentAccess,
                new DocumentWrites(world.documentAccess)).load("acme-v1", "ACME-VPN").orElseThrow().text());
    }

    @Test
    void theAnswerToWhereTheSurveyDocGoesIsReadFromTheUsersWords() {
        assertThat(SurveyFeedTool.promotionChoice(SurveyFeedTool.PROMOTE_BESIDE)).isEqualTo(SurveyFeedTool.BESIDE);
        assertThat(SurveyFeedTool.promotionChoice(SurveyFeedTool.KEEP_IN_BUILD)).isEqualTo(SurveyFeedTool.KEEP);
        assertThat(SurveyFeedTool.promotionChoice(SurveyFeedTool.DECIDE_LATER)).isEqualTo(SurveyFeedTool.LATER);
        assertThat(SurveyFeedTool.promotionChoice("not sure")).isEqualTo(SurveyFeedTool.LATER);
    }

    // JSON lines wrapped with a source field, each source's messages in a format of its own.
    private static String wrappedLines(final int count) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            final String message = switch (i % 3) {
                case 0 -> "{\\\"eventType\\\": \\\"" + (i % 2 == 0
                        ? "login"
                        : "logout") + "\\\", \\\"outcome\\\": \\\"failure\\\"}";
                case 1 -> i % 2 == 0
                        ? "Executing query took " + i + "ms"
                        : "Connection pool grew to " + i;
                default -> "Request: <Envelope><Body>" + i + "</Body></Envelope>";
            };
            final String source = List.of("Security", "Database", "Integration").get(i % 3);
            out.append("{\"app\": \"acme\", \"source\": \"").append(source).append("\", \"message\": \"")
                    .append(message).append("\"}\n");
        }
        return out.toString();
    }

    @Test
    void aKindFieldTheUserGivesIsSpeltAsTheRecordsSpellItOrRefusedWhenTheyLackIt() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, msgActCsv(), false);

        final Map<String, Object> cased = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                    + "\"build\": \"acme-v1\", \"kind_field\": \"MSG_ACT\"}"));
        assertThat(cased.get(SurveyDoc.KIND_FIELDS)).isEqualTo(Map.of("fields", List.of("msg_act"), "by", "user"));
        assertThat(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                        + "\"kind_field\": \"verdict\"}").text())
                .contains("names no field of the records", "msg_act");
    }

    @Test
    void anotherKindFieldTheUserGivesStartsTheSurveyAfresh() {
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, msgActCsv(), false);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\", "
                  + "\"kind_field\": \"msg_act\"}"));

        final Map<String, Object> afresh = json(call("{\"stream_ids\": [7], \"max_streams\": 1, "
                                                     + "\"build\": \"acme-v1\", \"kind_field\": \"direction\"}"));

        assertThat(afresh.get(SurveyDoc.KIND_FIELDS)).isEqualTo(Map.of("fields", List.of("direction"), "by",
                "user"));
        assertThat((String) afresh.get("kind_field_changed")).contains("started afresh");
        assertThat(String.valueOf(afresh.get("shapes"))).contains("row:direction=inbound")
                .doesNotContain("msg_act=");
    }

    @Test
    void theUserMayGoAheadWithTheSurveyAsItIs() {
        // Seen in testing: a feed's survey couldn't be covered, the user asked to go ahead with a limited sample, and
        // there was nowhere to record it, so the agent kept surveying.
        sampleFeedInABuild();
        stream(7, "ACME-VPN", 0, logins(3), false);
        json(call("{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\"}"));

        // Without forms, "true" alone isn't the user's word: the agent asks them.
        assertThat(json(call("{\"feed\": \"ACME-VPN\", \"build\": \"acme-v1\", \"accept_as_is\": \"true\"}")))
                .containsEntry("status", "needs_user");
        // The user says no: the survey goes on.
        assertThat(json(call("{\"feed\": \"ACME-VPN\", \"build\": \"acme-v1\", \"accept_as_is\": "
                             + "\"no, keep going\"}"))).containsKey("user_says").doesNotContainKey("saturated");

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", SurveyFeedTool.ACCEPT));
        final Map<String, Object> accepted = json(call("{\"feed\": \"ACME-VPN\", \"build\": \"acme-v1\", "
                                                       + "\"accept_as_is\": \"true\"}", user.context()));

        assertThat(user.shown()).singleElement().asString().contains("1 stream(s) of feed 'ACME-VPN' surveyed");
        assertThat(accepted).containsEntry("saturated", true).containsKey(SurveyDoc.ACCEPTED);
        assertThat((String) accepted.get("coverage")).startsWith("covered as far as the user chose");
    }

    @Test
    void aStreamTooBigToReadFurtherIsSettledWhenTheUserStopsThere() {
        // Seen in testing: three 20,000-30,000-record streams could never be reported read whole; the user chose to
        // stop and accept them, the choice had nowhere to go, and the agent was told to raise max_chars_per_stream.
        sampleFeedInABuild();
        final Meta meta = meta(7, "ACME-VPN", 0);
        Mockito.when(streamReader.meta(7L)).thenReturn(meta);
        // More parts than a call reads.
        Mockito.when(streamReader.readHead(eq(7L), anyInt(), anyInt()))
                .thenReturn(new StreamReader.RawHead(logins(3), false, 25));
        final String args = "{\"stream_ids\": [7], \"max_streams\": 1, \"build\": \"acme-v1\"";

        final Map<String, Object> asked = json(call(args + "}"));
        // Said plainly: how much was read, and why no more can be (seen: "records read: per_stream").
        assertThat((String) asked.get("coverage")).startsWith("NOT COVERED: The survey read only the start of stream "
                                                             + "7, too big to read whole in one go, and can't read "
                                                             + "further into it. Stream 7: 60 records from its "
                                                             + "start; it has 25 parts, more than a call reads (");
        assertThat((String) asked.get("hint")).contains("stop and accept", "Don't raise max_chars_per_stream");

        final Map<String, Object> stopped = json(call(args + ", \"read_deeper\": \"stop\"}"));
        assertThat(stopped).containsEntry("saturated", true)
                .containsEntry(SurveyDoc.STOPPED_STREAMS, List.of(7));
        assertThat((String) stopped.get("coverage")).startsWith("covered as far as the user chose: stream(s) [7]");
        // Kept: read again, the stream isn't asked about, and the survey stays covered.
        final Map<String, Object> again = json(call(args + "}"));
        assertThat(again).containsEntry("saturated", true);
    }

    // A CSV mail log whose kind is in msg_act, a name nothing knows.
    private static String msgActCsv() {
        final StringBuilder text = new StringBuilder("timestamp,sender,subject,msg_act,direction\n");
        for (int i = 0; i < 30; i++) {
            text.append("2026-11-11T08:%02d:04Z,u%d@example.com,Subject %d,%s,%s\n".formatted(i, i, i,
                    List.of("allow", "quarantine", "reject").get(i % 3), i % 2 == 0
                            ? "inbound"
                            : "outbound"));
        }
        return text.toString();
    }

    // The build's feed, which the sample streams were uploaded to.
    // Key=value login records, one a line.
    private static String logins(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "time=2026-10-01T10:%02d:00Z action=login user=u%d\n".formatted(i, i))
                .collect(Collectors.joining());
    }

    private void sampleFeedInABuild() {
        final DocRef feed = world.inBuild("acme-v1", FeedDoc.TYPE, "ACME-VPN", "feed-1");
        Mockito.when(feedNames.feedsNamed("ACME-VPN")).thenReturn(List.of(feed));
    }

    private Meta stream(final long id, final String feed, final long createMs, final String text,
                        final boolean truncated) {
        final Meta meta = meta(id, feed, createMs);
        Mockito.when(streamReader.meta(id)).thenReturn(meta);
        Mockito.when(streamReader.readHead(eq(id), anyInt(), anyInt()))
                .thenReturn(new StreamReader.RawHead(text, truncated, 1));
        return meta;
    }

    private static Meta meta(final long id, final String feed, final long createMs) {
        return Meta.builder().id(id).feedName(feed).typeName("Raw Events").status(Status.UNLOCKED)
                .createMs(createMs).build();
    }

    private static ToolSession session() {
        return new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));
    }

    private ToolResult call(final String arguments) {
        return call(arguments, BuildToolCalls.context());
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        final ToolCall call = BuildToolCalls.call(SurveyFeedTool.NAME, arguments);
        return tool.execute(call, context);
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return new LinkedHashMap<>(JsonUtil.readValue(result.text(), Map.class));
    }
}
