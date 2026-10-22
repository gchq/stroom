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

import stroom.ai.impl.tools.StreamReader;
import stroom.data.shared.UploadDataRequest;
import stroom.data.store.api.DataService;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.StroomStreamException;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// Sending sample data into a feed (the Stroom MCP server's `utils/uploads.py` `send_to_feed` and `commands`, and
/// their tests in `tests/test_uploads.py`).
class TestFeedUploads {

    @TempDir
    Path dir;

    private final ResourceStore resourceStore = Mockito.mock(ResourceStore.class);
    private final DataService dataService = Mockito.mock(DataService.class);
    private final AttributeMapFilterFactory filterFactory = Mockito.mock(AttributeMapFilterFactory.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final FeedUploads uploads = new FeedUploads(() -> resourceStore, () -> dataService, () -> filterFactory,
            streamReader, securityContext);
    private final List<UploadDataRequest> stored = new ArrayList<>();
    private final List<String> storedText = new ArrayList<>();
    private final List<AttributeMap> checked = new ArrayList<>();
    private AttributeMapFilter filter = attributes -> {
        checked.add(attributes);
        return true;
    };

    @BeforeEach
    void setUp() {
        final ResourceKey key = new ResourceKey("k1", "sample");
        Mockito.when(resourceStore.createTempFile(anyString())).thenReturn(key);
        Mockito.when(resourceStore.getTempFile(key)).thenReturn(dir.resolve("upload"));
        Mockito.doAnswer(inv -> {
            Files.deleteIfExists(dir.resolve("upload"));
            return null;
        }).when(resourceStore).deleteTempFile(key);
        Mockito.when(dataService.upload(any())).thenAnswer(inv -> {
            final UploadDataRequest request = inv.getArgument(0);
            stored.add(request);
            storedText.add(Files.readString(dir.resolve("upload")));
            return request.getKey();
        });
        Mockito.when(filterFactory.create()).thenAnswer(inv -> filter);
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("alice");
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(Meta.builder().id(77).createMs(System.currentTimeMillis() + 5000).build(), null,
                        Map.of()))));
        Mockito.when(streamReader.streamAttributes(anyLong())).thenReturn(Map.of("stream_id", 77L, "attributes",
                Map.of("ReceiptId", "receipt-1")));
    }

    @Test
    void theWholeFileIsCheckedThenStoredAsStroomsUploadStoresItAsTheUser() {
        // Far past the 2,000 characters a reader gave the agent.
        final String body = "[" + ",".repeat(800_000) + "]";
        final Map<String, String> receipt = new LinkedHashMap<>();
        receipt.put("Type", "Raw Reference");
        receipt.put("EffectiveTime", "2000-01-01T00:00:00.000Z");
        receipt.put("MyHost", "ws01");

        final FeedUploads.Sent sent = uploads.send("FORTIOS-EVENTS-V1.0", "Raw Reference", receipt, "001_1.json",
                input(body), 1024 * 1024);

        assertThat(sent.toMap()).isEqualTo(Map.of("receipt_id", "receipt-1", "stream_id", 77L, "bytes",
                (long) body.length()));
        assertThat(storedText).containsExactly(body);
        final UploadDataRequest request = stored.getFirst();
        assertThat(request.getFeedName()).isEqualTo("FORTIOS-EVENTS-V1.0");
        assertThat(request.getStreamTypeName()).isEqualTo("Raw Reference");
        assertThat(request.getFileName()).isEqualTo("001_1.json");
        assertThat(request.getEffectiveMs()).isEqualTo(946_684_800_000L);
        assertThat(request.getMetaData()).isEqualTo("MyHost:ws01\n");
        // Stroom's receipt checks saw the feed, its type, the headers and who sent it.
        final AttributeMap attributes = checked.getFirst();
        assertThat(attributes.get("Feed")).isEqualTo("FORTIOS-EVENTS-V1.0");
        assertThat(attributes.get("Type")).isEqualTo("Raw Reference");
        assertThat(attributes.get("MyHost")).isEqualTo("ws01");
        assertThat(attributes.get("UploadedBy")).isEqualTo("alice");
    }

    @Test
    void dataTheReceiptChecksDropOrRejectIsNotStored() {
        filter = attributes -> false;
        assertThatThrownBy(() -> send("a=1")).isInstanceOf(ToolRefusal.class)
                .hasMessage("Stroom's receipt policy drops data for feed ACME (Raw Events): nothing was stored. "
                            + "Ask an administrator about the feed's receipt rules.");

        filter = attributes -> {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributes);
        };
        assertThatThrownBy(() -> send("a=1")).isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Stroom refused data for feed ACME: ");
        assertThat(stored).isEmpty();
    }

    @Test
    void tooMuchOrNoDataIsRefusedAndNothingStored() {
        assertThatThrownBy(() -> uploads.send("ACME", "Raw Events", Map.of(), "big.json",
                input("x".repeat(1024 * 1024 + 1)), 1024 * 1024)).isInstanceOf(FeedUploads.TooLarge.class)
                .hasMessage("Larger than 1 MB: send a part of the file, or send it to Stroom directly");
        assertThatThrownBy(() -> send("")).isInstanceOf(FeedUploads.NoData.class)
                .hasMessage("No data: give the file with --data-binary @<file>");
        assertThat(stored).isEmpty();
        assertThat(dir.resolve("upload")).doesNotExist();
    }

    @Test
    void whatStroomRefusesToStoreIsSaid() {
        Mockito.doThrow(new PermissionException(null, "You do not have permission to update feed ACME"))
                .when(dataService).upload(any());

        assertThatThrownBy(() -> send("a=1")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Stroom didn't store the data in feed ACME: ")
                .hasMessageContaining("permission to update feed ACME");
    }

    @Test
    void aStreamNotVisibleYetHasNoId() {
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(Meta.builder().id(5).createMs(1).build(), null, Map.of()))));

        assertThat(send("a=1").toMap()).containsEntry("stream_id", null).containsEntry("receipt_id", null);
    }

    @Test
    void anEffectiveTimeThatIsntOneIsRefused() {
        assertThatThrownBy(() -> uploads.send("ACME", "Raw Reference", Map.of("EffectiveTime", "yesterday"),
                "sample", input("a"), 10)).isInstanceOf(ToolRefusal.class)
                .hasMessage("effective_time must be an ISO 8601 UTC time, e.g. 2000-01-01T00:00:00.000Z, not "
                            + "'yesterday'");
    }

    @Test
    void eachFileGetsACommandForBashAndPowerShell() {
        // The file's name in the URL: one quoted argument besides it, nothing to escape. An agent retyping a header
        // form dropped a quote, and PowerShell waited on the command for minutes. The user's own credential comes
        // from the terminal, never the chat.
        final List<Map<String, String>> commands = FeedUploads.commands("https://stroom.example/mcp/upload/Xk3f9QpL2mWa",
                List.of("sample-data/fortios/001_1.json"));

        assertThat(commands).hasSize(1);
        final Map<String, String> one = commands.getFirst();
        assertThat(one.get("file")).isEqualTo("sample-data/fortios/001_1.json");
        assertThat(one.get("bash")).isEqualTo("curl -sS --fail-with-body -H \"Authorization: Bearer "
                                              + "$STROOM_API_KEY\" -H 'Content-Type: application/octet-stream' "
                                              + "--data-binary '@sample-data/fortios/001_1.json' "
                                              + "'https://stroom.example/mcp/upload/Xk3f9QpL2mWa?file=sample-data"
                                              + "%2Ffortios%2F001_1.json'");
        // curl alone is Invoke-WebRequest in Windows PowerShell.
        assertThat(one.get("powershell")).startsWith("curl.exe -sS --fail-with-body -H \"Authorization: Bearer "
                                                     + "$env:STROOM_API_KEY\" -H 'Content-Type: "
                                                     + "application/octet-stream' --data-binary "
                                                     + "'@sample-data/fortios/001_1.json' ");
        assertThat(FeedUploads.commands("u", List.of("my file.json")).getFirst().get("bash"))
                .endsWith("'u?file=my%20file.json'");
    }

    @Test
    void aPathIsQuotedSoNeitherShellExpandsAnythingInIt() {
        // Seen in review: the path went in double quotes with only " escaped, so $(...) and backticks in a path
        // the agent gave would run in the user's terminal.
        final Map<String, String> sub = FeedUploads.commands("u", List.of("$(id).json")).getFirst();
        assertThat(sub.get("bash")).contains("--data-binary '@$(id).json' ");
        assertThat(sub.get("powershell")).contains("--data-binary '@$(id).json' ");

        final Map<String, String> ticks = FeedUploads.commands("u", List.of("`whoami`.json")).getFirst();
        assertThat(ticks.get("bash")).contains("--data-binary '@`whoami`.json' ");
        assertThat(ticks.get("powershell")).contains("--data-binary '@`whoami`.json' ");

        final Map<String, String> quotes = FeedUploads.commands("u", List.of("it's \"here\".json")).getFirst();
        assertThat(quotes.get("bash")).contains("--data-binary '@it'\\''s \"here\".json' ");
        assertThat(quotes.get("powershell")).contains("--data-binary '@it''s \"here\".json' ");

        final Map<String, String> backslash = FeedUploads.commands("u", List.of("C:\\data\\")).getFirst();
        assertThat(backslash.get("bash")).contains("--data-binary '@C:\\data\\' ");
        assertThat(backslash.get("powershell")).contains("--data-binary '@C:\\data\\' ");
        // The URL has the name encoded, so nothing in it needs quoting either.
        assertThat(sub.get("bash")).endsWith("'u?file=%24%28id%29.json'");
    }

    @Test
    void aPathWithALineBreakOrControlCharacterIsRefused() {
        assertThatThrownBy(() -> FeedUploads.commands("u", List.of("a.json\nrm -rf ~")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("line break or another control character");
        assertThatThrownBy(() -> FeedUploads.commands("u", List.of("a\u0007.json")))
                .isInstanceOf(ToolRefusal.class);
    }

    private FeedUploads.Sent send(final String text) {
        return uploads.send("ACME", "Raw Events", Map.of("Type", "Raw Events"), "sample", input(text), 1024);
    }

    private static ByteArrayInputStream input(final String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
