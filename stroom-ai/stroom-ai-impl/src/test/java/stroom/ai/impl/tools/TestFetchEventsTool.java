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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.data.shared.StreamTypeNames;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;

import event.logging.ViewEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// fetch_events reading records from one stream.
class TestFetchEventsTool {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final StreamWorld world = new StreamWorld();
    private FetchEventsTool tool;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        tool = new FetchEventsTool(fixture.workspace, () -> world.store, () -> fixture.securityContext,
                fixture.auditLog(), world.streamReader());
    }

    @Test
    void aRecordTheSizeLimitWouldCutIsLeftForTheNextPage() {
        // Seen: 100 events of a large stream read at once, the last cut mid-attribute and validated as "attributes
        // construct error". Whole records only, and where to read on.
        final String event = TestStreamReader.EVENT;
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, Collections.nCopies(10, event));

        final Map<String, Object> read = tool.readRecords(7, 0, 5, null, event.length() * 3 - 5);

        assertThat(records(read)).hasSize(2);
        assertThat(read.get("next_first_record")).isEqualTo(2);
        assertThat(read).doesNotContainKey("truncated");
        // The limit is characters, which the user may raise.
        assertThat((String) read.get("hint")).contains("not of records");
        // A single record larger than the limit is still returned (cut), flagged.
        final Map<String, Object> one = tool.readRecords(7, 0, 5, null, 40);
        assertThat(records(one)).hasSize(1);
        assertThat(one.get("truncated")).isEqualTo(true);
        assertThat((String) one.get("hint")).contains("maxChars");
        // Raised, all five fit.
        final Map<String, Object> more = tool.readRecords(7, 0, 5, null, event.length() * 5 + 10);
        assertThat(records(more)).hasSize(5);
        assertThat(more).doesNotContainKey("truncated");
    }

    @Test
    void aRawStreamHeldAsOneRecordIsReadByItsRecords() {
        // Seen in production: reads of records 200, 450, 700 and 950 of a 1,000-fragment sample each returned its
        // first four, as Stroom holds a raw stream as one record; the agent went on to parse the file on disk in
        // PowerShell.
        final List<String> lines = IntStream.range(0, 1000)
                .mapToObj(n -> "<Event xmlns=\"urn:win\"><Id>" + n + "</Id></Event>")
                .toList();
        world.addRaw(7, String.join("\n", lines) + "\n");

        final Map<String, Object> read = tool.readRecords(7, 450, 2, null, 60_000);

        assertThat(records(read)).isEqualTo(lines.subList(450, 452));
        assertThat(read.get("total_records")).isEqualTo(1000);
        assertThat((String) read.get("records_are")).contains("xml fragments");
        final Map<String, Object> past = tool.readRecords(7, 1000, 2, null, 60_000);
        assertThat(records(past)).isEmpty();
        assertThat((String) past.get("hint")).contains("past the end");
        world.addRaw(8, "just one line of text");
        assertThat(records(tool.readRecords(8, 0, 5, null, 60_000))).containsExactly("just one line of text");
    }

    @Test
    void aStreamStroomWontShowAsTextIsReadThroughItsBytesSayingWhatWasReplaced() {
        world.addRaw(7, "");
        world.bytes.put(7L, TestStreamCharsets.MIXED);
        world.errors.put(7L, List.of("Error fetching data: Unable to decode a UTF-8 character starting at byte "
                                     + "offset 40."));

        final Map<String, Object> read = tool.readRecords(7, 0, 5, null, 60_000);

        assertThat(records(read).getFirst()).contains("latin1 caf�");
        assertThat(read.get("feed")).isEqualTo(StreamWorld.FEED);
        assertThat(read).containsKey("undecodable_bytes");
    }

    @Test
    void aStreamThatCantBeReadSaysWhy() {
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of("<Events/>"));
        world.errors.put(7L, List.of("You cannot view locked streams."));

        assertThatThrownBy(() -> tool.run(ToolTestFixture.request(FetchEventsTool.NAME, "{\"streamId\":7}"),
                ToolCalls.inChat(1, fixture.workspace)))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage("Stroom could not read stream 7: You cannot view locked streams.");
    }

    @Test
    void theRecordsComeBackAsJsonAndTheReadIsLogged() {
        world.addSegmented(7, StreamTypeNames.EVENTS, null, null, List.of("<Events/>"));

        final String text = tool.run(ToolTestFixture.request(FetchEventsTool.NAME, "{\"streamId\":7,"
                                                                                  + "\"recordCount\":1}"),
                ToolCalls.inChat(1, fixture.workspace)).text();

        assertThat(JsonUtil.readValue(text, Map.class))
                .containsEntry("stream_id", 7)
                .containsEntry("records", List.of("<Events/>"));
        assertThat(fixture.logged(ViewEventAction.class)).hasSize(1);
    }

    @Test
    void withoutViewDataNoStreamIsRead() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);

        assertThatThrownBy(() -> tool.run(ToolTestFixture.request(FetchEventsTool.NAME, "{\"streamId\":7}"),
                ToolCalls.inChat(1, fixture.workspace)))
                .hasMessageContaining("don't have permission to view data");
        Mockito.verifyNoInteractions(world.dataService, world.store);
    }

    @Test
    void neitherATableNorAStreamIsRefused() {
        assertThatThrownBy(() -> tool.run(ToolTestFixture.request(FetchEventsTool.NAME, "{}"),
                ToolCalls.inChat(1, fixture.workspace)))
                .hasMessageContaining("Give path");
    }

    @SuppressWarnings("unchecked")
    private static List<String> records(final Map<String, Object> read) {
        return (List<String>) read.get("records");
    }
}
