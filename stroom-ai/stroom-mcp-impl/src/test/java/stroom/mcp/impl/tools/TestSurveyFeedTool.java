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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// survey_feed (the Stroom MCP server's `tests/test_survey.py` tool cases, and `tests/test_progress.py`'s).
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
            world.documentAccess, auditLog);

    TestSurveyFeedTool() {
        Mockito.when(world.documentAccess.canViewData()).thenReturn(true);
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(ResultPage.empty());
    }

    @Test
    void fewerStreamsThanTheDefaultAreTheUsersToConfirmOnce() {
        // Seen in VS Code: an agent surveyed a live feed 3 streams at a time until the user asked for a larger
        // sample. A number the user gives is still theirs: fewer is confirmed in a form, then not asked again for
        // that feed.
        feedHoldsMany();
        // Without a form, the user can't confirm it: nothing is read.
        assertThat(call("{\"feed\": \"ACME-VPN\", \"max_streams\": 3}", PortedToolCalls.context()).text())
                .startsWith("Not done: Survey feed 'ACME-VPN' only 3 stream(s) a call (the default is 10).");
        Mockito.verify(streamReader, Mockito.never()).readHead(anyLong(), anyInt(), anyInt());

        final PortedToolCalls.FormUser user = PortedToolCalls.user(Map.of("value", true));
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
        assertThat((String) result.get("coverage")).contains("windows spread to their end were read")
                .contains("review_coverage")
                .doesNotContain("max_chars_per_stream=5000000");
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
        // Seen in testing on 0.18.13: an existing feed onboarded from 5 streams, given by id, so not asked about.
        final DocRef existing = new DocRef(FeedDoc.TYPE, "feed-0", "ACME-VPN");
        Mockito.when(feedNames.feedsNamed("ACME-VPN")).thenReturn(List.of(existing));
        for (int id = 1; id <= 5; id++) {
            stream(id, "ACME-VPN", 0, "{\"a\": 1}\n", false);
        }
        feedHoldsMany();
        final PortedToolCalls.FormUser user = PortedToolCalls.declining();
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
        final PortedToolCalls.FormUser user = PortedToolCalls.declining();

        assertThat(json(call("{\"stream_ids\": [1]}", user.context())).get("streams_read")).isEqualTo(List.of(1));
        assertThat(user.shown()).isEmpty();
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
                .startsWith("NOT COVERED: every stream in the feed was picked, but only the start of stream(s) "
                            + "[15911122]")
                .contains("stream_ids=[15911122]");

        stream(15911122, "SecretServer", 1_760_000_000_000L, records, false);
        final Map<String, Object> whole = json(call("{\"feed\": \"SecretServer\"}"));
        assertThat(whole.get("saturated")).isEqualTo(true);
        assertThat(whole.get("coverage")).isEqualTo("covered: every stream in the feed has been read whole");
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
    void everyStreamReadSaysTheSurveyIsComplete() {
        final Map<String, Object> result = json(call("{\"feed\": \"ACME\", \"skip_stream_ids\": [1, 2]}"));

        assertThat(result.get("saturated")).isEqualTo(true);
        assertThat(result.get("hint")).isEqualTo("Every stream in the feed has been read: the survey is complete.");
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
        assertThat(tool.isReadOnly(PortedToolCalls.call(SurveyFeedTool.NAME, "{\"feed\": \"ACME\"}"))).isTrue();
        assertThat(tool.isReadOnly(PortedToolCalls.call(SurveyFeedTool.NAME, "{\"feed\": \"ACME\", \"build\": "
                                                                             + "\"b\"}"))).isFalse();
        assertThat(tool.hasReadOnlyCalls()).isTrue();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.usesRunSlot()).isTrue();
        assertThat(tool.category()).isEqualTo(AiToolCategory.PROCESSING);
    }

    // A feed holding more streams than a call asks for: as many as asked for, but none to pick for a survey (whose
    // first find is of one, the newest).
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

    // The build's feed, which the sample streams were uploaded to.
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
        return call(arguments, PortedToolCalls.context());
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        final ToolCall call = PortedToolCalls.call(SurveyFeedTool.NAME, arguments);
        return tool.execute(call, context);
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return new LinkedHashMap<>(JsonUtil.readValue(result.text(), Map.class));
    }
}
