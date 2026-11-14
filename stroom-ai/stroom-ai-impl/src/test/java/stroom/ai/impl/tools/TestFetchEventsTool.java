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

import java.nio.charset.StandardCharsets;
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
    void aRecordOfAnyPartIsReadWhereverItIsAsAPipelineNumbersIt() {
        // Seen: review_coverage gave a missed kind at part 0, record 9,000 of a two-part 211 MB CSV stream; read as
        // Stroom's record 9,000, it gave the part's start, header and all, twice, and the agent asked the user how
        // to translate the kind without having seen one.
        final String header = "action,user,note";
        final StringBuilder first = new StringBuilder(header).append('\n');
        // Past the first million characters read of a part, and a quoted value holding a new line on the way.
        final String padding = "x".repeat(200);
        for (int n = 0; n < 9000; n++) {
            first.append(n == 10
                    ? "allow,u10,\"two\nlines\""
                    : "allow,u" + n + "," + padding).append('\n');
        }
        first.append("bypass,u9000,the one\n").append("allow,u9001,after\n");
        world.addRaw(7, "");
        world.parts.put(7L, List.of(first.toString().getBytes(StandardCharsets.UTF_8),
                (header + "\nblock,v0,second part\nbypass,v1,second part\n").getBytes(StandardCharsets.UTF_8)));

        final Map<String, Object> deep = tool.readRecords(7, 0, 9000, 1, null, 60_000);

        assertThat(records(deep)).containsExactly("bypass,u9000,the one");
        assertThat(deep.get("header")).isEqualTo(header);
        assertThat(deep.get("parts")).isEqualTo(2);
        assertThat((String) deep.get("records_are")).contains("read through part 0");
        // The record with a quoted new line is one record, as the Data Splitter reads it.
        assertThat(records(tool.readRecords(7, 0, 10, 1, null, 60_000))).containsExactly("allow,u10,\"two\nlines\"");
        // The second part, numbered from its own start.
        final Map<String, Object> second = tool.readRecords(7, 1, 1, 1, null, 60_000);
        assertThat(records(second)).containsExactly("bypass,v1,second part");
        assertThat(second.get("part")).isEqualTo(1);
        assertThat(second.get("total_records")).isEqualTo(2);
        // A part or record the stream doesn't have says so, rather than giving another.
        assertThat((String) tool.readRecords(7, 2, 0, 1, null, 60_000).get("hint")).contains("part runs from 0 to 1");
        final Map<String, Object> past = tool.readRecords(7, 0, 9100, 1, null, 60_000);
        assertThat(records(past)).isEmpty();
        assertThat((String) past.get("hint")).contains("Records run from 0 to 9001 in part 0");
    }

    @Test
    void eachPartsReceiptHeadersAreReadForThePartAskedFor() {
        // Seen (eval 59): fetch_events childType 'Meta Data' gave part 0's headers (dc01) for every firstRecord
        // and part asked of a five-part stream, though it said total_records 5; the agent never saw the other
        // hosts' roles.
        world.addRaw(7, "{\"a\":1}\n");
        final List<String> hosts = List.of("dc01", "fs01", "ws-0142", "web01", "ws-0187");
        world.partHeaders.put(7L, hosts.stream()
                .map(host -> "Feed:" + StreamWorld.FEED + "\nRemoteHost:" + host + ".corp.example\n")
                .toList());

        final Map<String, Object> third = tool.readRecords(7, 0, 2, 1, StreamTypeNames.META, 60_000);
        assertThat(records(third)).singleElement().asString().contains("RemoteHost:ws-0142.corp.example");
        assertThat(third.get("first_record")).isEqualTo(2);

        final Map<String, Object> byPart = tool.readRecords(7, 4, 0, 1, StreamTypeNames.META, 60_000);
        assertThat(records(byPart)).singleElement().asString().contains("RemoteHost:ws-0187.corp.example");
        assertThat(byPart.get("first_record")).isEqualTo(4);

        // A range of parts, each its own.
        final Map<String, Object> all = tool.readRecords(7, 0, 0, 10, StreamTypeNames.META, 60_000);
        assertThat(records(all)).hasSize(5);
        for (int i = 0; i < hosts.size(); i++) {
            assertThat(records(all).get(i)).contains("RemoteHost:" + hosts.get(i) + ".corp.example");
        }
        assertThat(all.get("parts")).isEqualTo(5L);
        assertThat((String) all.get("records_are")).contains("part(s) 0 to 4").contains("record n is part n");
        assertThat(all).doesNotContainKey("next_first_record");

        // From the middle, read on where the range stops.
        final Map<String, Object> some = tool.readRecords(7, 0, 2, 2, StreamTypeNames.META, 60_000);
        assertThat(records(some)).hasSize(2);
        assertThat(records(some).get(1)).contains("web01");

        // Past the last part, says what there is.
        final Map<String, Object> past = tool.readRecords(7, 0, 5, 1, StreamTypeNames.META, 60_000);
        assertThat(records(past)).isEmpty();
        assertThat((String) past.get("hint")).contains("runs from 0 to 4");
    }

    @Test
    void theReceiptHeadersOfAStreamOfOnePartAreItsOwn() {
        world.addRaw(7, "{\"a\":1}\n");
        world.headers.put(7L, "Feed:" + StreamWorld.FEED + "\nRemoteHost:solo\n");

        final Map<String, Object> read = tool.readRecords(7, 0, 5, StreamTypeNames.META, 60_000);

        assertThat(records(read)).singleElement().asString().contains("RemoteHost:solo");
        assertThat(read).doesNotContainKeys("parts", "hint");
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
                .hasMessageStartingWith("Stroom could not read stream 7: You cannot view locked streams. It is most "
                                        + "likely still being written");
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
