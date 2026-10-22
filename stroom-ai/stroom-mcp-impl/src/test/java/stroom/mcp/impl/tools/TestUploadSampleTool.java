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
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolResult;
import stroom.config.common.UriFactory;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// upload_sample (the Stroom MCP server's `tools/feeds.py` `upload_sample` and `upload_ticket`, and their tests in
/// `tests/test_uploads.py` and `tests/test_samples.py`).
class TestUploadSampleTool {

    private final FeedWorld feeds = new FeedWorld();
    private final FeedUploads feedUploads = Mockito.mock(FeedUploads.class);
    private final AiConfig aiConfig = Mockito.mock(AiConfig.class);
    private final UriFactory uriFactory = Mockito.mock(UriFactory.class);
    private final SampleUploads sampleUploads = new SampleUploads(() -> aiConfig, () -> 1_000_000L);
    private final UploadSampleTool tool = new UploadSampleTool(feeds.buildFeeds, feeds.world.guard(),
            feeds.world.records(), feeds.plan, feedUploads, sampleUploads, feeds.securityContext, () -> aiConfig,
            () -> uriFactory);
    private final List<byte[]> sent = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig());
        Mockito.when(feeds.securityContext.hasAppPermission(any(AppPermission.class))).thenReturn(true);
        Mockito.when(feeds.securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("alice").build());
        Mockito.when(uriFactory.publicUri(anyString())).thenAnswer(inv ->
                URI.create("https://stroom.example" + inv.getArgument(0)));
        Mockito.when(feedUploads.send(anyString(), anyString(), anyMap(), anyString(), any(InputStream.class),
                anyLong())).thenAnswer(inv -> {
                    final byte[] data = inv.<InputStream>getArgument(4).readAllBytes();
                    sent.add(data);
                    return new FeedUploads.Sent("receipt-1", 77L, data.length);
                });
    }

    @Test
    void pastedTextGoesToTheBuildsFeedAsTheUser() {
        feeds.buildFeed("acme", "ACME-EVENTS");

        final Map<String, Object> result = json(call("{\"feed\": \"ACME-EVENTS\", \"sample\": \"a=1 b=2\\n\", "
                                                     + "\"headers\": {\"MyHost\": \"ws01\"}}"));

        assertThat(result).containsEntry("feed", "ACME-EVENTS").containsEntry("receipt_id", "receipt-1")
                .containsEntry("stream_id", 77).containsEntry("bytes", 8).containsKey("next");
        assertThat(String.valueOf(result.get("hint"))).startsWith("One stream per sample file");
        assertThat(new String(sent.getFirst(), StandardCharsets.UTF_8)).isEqualTo("a=1 b=2\n");
        assertThat(receipt()).isEqualTo(Map.of("Type", "Raw Events", "MyHost", "ws01"));
    }

    @Test
    void aStreamNotVisibleYetIsSaidSo() {
        feeds.buildFeed("acme", "ACME-EVENTS");
        Mockito.when(feedUploads.send(anyString(), anyString(), anyMap(), anyString(), any(InputStream.class),
                anyLong())).thenReturn(new FeedUploads.Sent("receipt-1", null, 3));

        final Map<String, Object> result = json(call("{\"feed\": \"ACME-EVENTS\", \"sample\": \"a=1\"}"));

        // A null stream_id is left out, as every ported tool's JSON leaves nulls out.
        assertThat(result).containsEntry("receipt_id", "receipt-1").doesNotContainKey("stream_id")
                .doesNotContainKey("bytes");
        assertThat(String.valueOf(result.get("hint"))).contains("check stream_meta feeds=['ACME-EVENTS'] shortly");
    }

    @Test
    void onceAFeedHasHadCommandsTextSamplesAreRefused() {
        // Seen: the terminal command failed, and the agent uploaded two records of each file as text instead.
        feeds.buildFeed("fortios", "FORTIOS-FIREWALL-V1.0");
        json(call("{\"feed\": \"FORTIOS-FIREWALL-V1.0\", \"files\": [\"001_1.json\"]}"));

        final ToolResult refused = call("{\"feed\": \"FORTIOS-FIREWALL-V1.0\", \"sample\": \"[{\\\"a\\\": 1}]\"}");

        assertThat(refused.text()).contains("samples are files on the user's disk").contains("exactly as given");
        assertThat(sent).isEmpty();
    }

    @Test
    void aReferenceSampleAppliesFromLongAgoUnlessToldOtherwise() {
        // Eval case 15 on Haiku: a directory uploaded without an effective time took effect when it arrived, after
        // the event samples, whose lookups then found nothing.
        feeds.buildFeed("acme", "ACME-USERS");
        feeds.buildFeed("acme", "ACME");

        call("{\"feed\": \"ACME-USERS\", \"sample\": \"user,name\\nalice,Alice\\n\", \"stream_type\": "
             + "\"Raw Reference\"}");
        assertThat(receipt()).containsEntry("EffectiveTime", "2000-01-01T00:00:00.000Z");
        call("{\"feed\": \"ACME-USERS\", \"sample\": \"user,name\\nalice,Alice\\n\", \"stream_type\": "
             + "\"Raw Reference\", \"effective_time\": \"2026-01-01T00:00:00.000Z\"}");
        assertThat(receipt()).containsEntry("EffectiveTime", "2026-01-01T00:00:00.000Z");
        call("{\"feed\": \"ACME\", \"sample\": \"a,b\\n1,2\\n\"}");
        assertThat(receipt()).doesNotContainKey("EffectiveTime");

        final Map<String, Object> files = json(call("{\"feed\": \"ACME-USERS\", \"files\": [\"users.csv\"], "
                                                    + "\"stream_type\": \"Raw Reference\"}"));
        final SampleUploads.Pending pending = waitingFor(files, "users.csv");
        assertThat(pending.headers()).isEqualTo(Map.of("EffectiveTime", "2000-01-01T00:00:00.000Z"));
        assertThat(pending.streamType()).isEqualTo("Raw Reference");
    }

    @Test
    void eachFileGetsACommandForTheUsersTerminalAndWaitsForIt() {
        final DocRef feed = feeds.buildFeed("fortios", "FORTIOS-EVENTS-V1.0");

        final Map<String, Object> result = json(call("{\"feed\": \"FORTIOS-EVENTS-V1.0\", \"files\": "
                                                     + "[\"sample-data/fortios/001_1.json\", \"002.json\"]}"));

        assertThat(result).containsEntry("feed", "FORTIOS-EVENTS-V1.0").containsEntry("expires_in_minutes", 15)
                .containsEntry("max_mb", 100);
        @SuppressWarnings("unchecked") final List<Map<String, String>> commands =
                (List<Map<String, String>>) result.get("commands");
        assertThat(commands).extracting(command -> command.get("file"))
                .containsExactly("sample-data/fortios/001_1.json", "002.json");
        assertThat(commands.getFirst().get("bash")).matches("curl -sS --fail-with-body -H \"Authorization: Bearer "
                                                            + "\\$STROOM_API_KEY\" -H 'Content-Type: "
                                                            + "application/octet-stream' --data-binary "
                                                            + "'@sample-data/fortios/001_1.json' "
                                                            + "'https://stroom.example/mcp/upload/[A-Za-z0-9_-]{12}"
                                                            + "\\?file=sample-data%2Ffortios%2F001_1.json'");
        final String hint = String.valueOf(result.get("hint"));
        assertThat(hint).contains("STROOM_API_KEY").contains("never ask for the key in the chat")
                .contains("build_status build=fortios").contains("The commands last 15 minute(s), each file once");
        // Nothing is sent now; the files wait for the user's terminal, as this user, for this feed.
        assertThat(sent).isEmpty();
        final SampleUploads.Pending pending = waitingFor(result, "002.json");
        assertThat(pending.feed()).isEqualTo("FORTIOS-EVENTS-V1.0");
        assertThat(pending.feedUuid()).isEqualTo(feed.getUuid());
        assertThat(pending.build()).isEqualTo("fortios");
        // Kept in the build's record: text isn't taken for the feed from now on.
        assertThat(feeds.world.records().entry("fortios", feed.getUuid()).get(BuildFeeds.SAMPLE_FILES))
                .isEqualTo(true);
    }

    @Test
    void filesWithoutTheMcpEndpointAreTheUsersToUploadInStroom() {
        feeds.buildFeed("fortios", "FORTIOS-EVENTS-V1.0");
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(false, null, null, null, null,
                null, null));

        assertThat(call("{\"feed\": \"FORTIOS-EVENTS-V1.0\", \"files\": [\"a.json\"]}").text())
                .contains("(/mcp/upload), which is turned off: the user uploads the files in Stroom instead")
                .contains("its Data tab, Upload");

        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig());
        Mockito.when(feeds.securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)).thenReturn(false);
        assertThat(call("{\"feed\": \"FORTIOS-EVENTS-V1.0\", \"files\": [\"a.json\"]}").text())
                .contains("needs the 'Use MCP Tools' permission");
    }

    @Test
    void withoutImportDataNothingIsSent() {
        feeds.buildFeed("acme", "ACME");
        Mockito.when(feeds.securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)).thenReturn(false);

        assertThat(call("{\"feed\": \"ACME\", \"sample\": \"a=1\"}").text())
                .isEqualTo("Sending data to feed ACME needs the '"
                           + AppPermission.IMPORT_DATA_PERMISSION.getDisplayValue() + "' permission, which the user "
                           + "doesn't have: ask an administrator for it.");
        assertThat(sent).isEmpty();
    }

    @Test
    void samplesGoOnlyToTheToolsOwnFeeds() {
        feeds.feedAt("System/Feeds", "PROD-FEED", "p1");

        assertThat(call("{\"feed\": \"PROD-FEED\", \"sample\": \"a=1\"}").text())
                .contains("was not created by this server");
        assertThat(call("{\"feed\": \"NO-SUCH-FEED\", \"sample\": \"a=1\"}").text())
                .startsWith("No feed named 'NO-SUCH-FEED' in this Stroom. If the user named it, they may mean a "
                            + "different Stroom cluster");
        assertThat(sent).isEmpty();
    }

    @Test
    void samplesGoToABuildOnAnExistingFeedOnlyThroughAFeedMadeForTheUsersFiles() {
        // The test feed the server makes for the Events (output_for) takes no samples either.
        final DocRef testFeed = feeds.buildFeed("bb", "VPN-GATEWAY-MCP-TEST");
        feeds.world.records().update("bb", testFeed, entry -> entry.put(BuildFeeds.OUTPUT_FOR, List.of("VPN-GATEWAY")));
        feeds.facts.sources = List.of("VPN-GATEWAY");

        assertThat(call("{\"feed\": \"VPN-GATEWAY-MCP-TEST\", \"sample\": \"a=1\"}").text())
                .contains("wasn't made for sample files the user chose instead: upload nothing");

        feeds.world.records().update("bb", testFeed, entry -> entry.put(BuildFeeds.USER_SAMPLES,
                List.of("samples/vpn_1.csv")));
        assertThat(json(call("{\"feed\": \"VPN-GATEWAY-MCP-TEST\", \"sample\": \"a=1\"}"))).containsEntry("stream_id",
                77);
    }

    @Test
    void aPathOrNothingIsRefusedWithWhatToGive() {
        feeds.buildFeed("acme", "FEED");

        assertThat(call("{\"feed\": \"FEED\", \"sample\": \"c:\\\\Users\\\\x\\\\001_1.json\"}").text())
                .contains("read the file and pass its text");
        assertThat(call("{\"feed\": \"FEED\"}").text()).isEqualTo("Give files (the sample files' paths on the "
                                                                  + "user's disk) or sample (only text the user "
                                                                  + "pasted into the chat)");
        assertThat(sent).isEmpty();
    }

    @Test
    void theFeedSaysHowTheUserSendsAFileThemselves() {
        final String said = UploadSampleTool.sentByUser(StroomLinks.absolute("https://stroom.example"),
                new DocRef(FeedDoc.TYPE, "f1", "FORTIOS-EVENTS-V1.0"));

        assertThat(said).startsWith("Next: upload_sample feed=FORTIOS-EVENTS-V1.0 files=[their paths]");
        // Seen (Qwen3 Coder Next): the user's curl, token placeholder and all, run from the agent's terminal three
        // times.
        assertThat(said).doesNotContain("curl").doesNotContain("Bearer")
                .contains("Never send files to Stroom's datafeed yourself")
                .contains("docType=Feed&docUuid=f1").contains("stream_meta feeds=['FORTIOS-EVENTS-V1.0']");
    }

    @Test
    void itIsAWriteApprovedAsAWhole() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.category().name()).isEqualTo("PROCESSING");
    }

    // The upload waiting for the file, as the servlet takes it with the id in the commands' URL.
    @SuppressWarnings("unchecked")
    private SampleUploads.Pending waitingFor(final Map<String, Object> result, final String file) {
        final String bash = ((List<Map<String, String>>) result.get("commands")).getFirst().get("bash");
        final Matcher id = Pattern.compile("/mcp/upload/([^?]+)\\?").matcher(bash);
        assertThat(id.find()).isTrue();
        return sampleUploads.claim(id.group(1), "alice", file).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> receipt() {
        final ArgumentCaptor<Map<String, String>> receipt = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(feedUploads, Mockito.atLeastOnce()).send(anyString(), anyString(), receipt.capture(),
                eq("sample"), any(InputStream.class), anyLong());
        return receipt.getValue();
    }

    private ToolResult call(final String arguments) {
        return tool.execute(PortedToolCalls.call(UploadSampleTool.NAME, arguments), PortedToolCalls.context());
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }
}
