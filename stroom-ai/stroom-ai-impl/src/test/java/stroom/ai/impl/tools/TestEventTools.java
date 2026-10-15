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

import stroom.ai.api.AiService;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.collection.api.CollectionService;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventTools {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                         + "<Events xmlns=\"event-logging:3\">\n";
    private static final String FOOTER = "</Events>\n";

    @TempDir
    Path tempDir;

    @Inject
    private AiDao aiDao;
    @Inject
    private WorkspaceStore workspaceStore;
    @Inject
    private AiDbConnProvider aiDbConnProvider;
    @Inject
    private ChatWorkspace workspace;

    private final AiService aiService = Mockito.mock(AiService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final Store store = Mockito.mock(Store.class);
    // The segments of each stream the user may see: header, events, footer.
    private final Map<Long, List<String>> streams = new HashMap<>();
    private AiConfig aiConfig = new AiConfig();
    private int chatId;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                        bind(TempDirProvider.class).toInstance(() -> tempDir);
                        bind(SecurityContext.class).toInstance(securityContext);
                        bind(AiService.class).toInstance(aiService);
                        bind(AiConfig.class).toProvider(() -> aiConfig);
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        chatId = chat.getId();
        Mockito.when(aiService.getChat(chatId)).thenReturn(chat);
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
        Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation -> source(invocation.getArgument(0)));

        streams.put(11L, List.of(HEADER,
                event("fw1", "Logon", "Success", null),
                event("fw2", "Logon", "Failure", "Bad password"),
                event("fw1", "Logoff", "Success", null),
                FOOTER));
        streams.put(12L, List.of(HEADER, event("fw3", "Logon", "Failure", "Account locked"), FOOTER));
        // A dashboard table with a hidden row of another feed (stream 99), which the user may not see.
        attach("/attachments/5.md", """
                | User | StreamId | EventId |
                | --- | --- | --- |
                | alice | 11 | 1 |
                | bob | 11 | 2 |
                | bob | 11 | 2 |
                | carol | 12 | 1 |
                | dave | 99 | 1 |
                | erin | 11 | 3 |
                """);
    }

    @Test
    void fetchEventsWritesEachEventWithWhereItCameFrom() {
        final String result = run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        assertThat(result)
                .contains("Wrote 4 event(s) from 2 stream(s) to /outputs/events.xml")
                // Stream 99 can't be read by this user.
                .contains("1 event(s) couldn't be read");
        final String file = read("/outputs/events.xml");
        assertThat(file)
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<stroom-events>")
                .contains("<stroom-event streamId=\"11\" eventId=\"2\"><Events xmlns=\"event-logging:3\">")
                .contains("<stroom-event streamId=\"12\" eventId=\"1\">")
                .doesNotContain("streamId=\"99\"")
                // The repeated row is fetched once.
                .containsOnlyOnce("eventId=\"2\"");
        // Each stream is opened once.
        Mockito.verify(store).openSource(11L);
    }

    @Test
    void fetchEventsReadsEachStreamInOrder() {
        attach("/attachments/8.md", """
                | StreamId | EventId |
                | --- | --- |
                | 11 | 3 |
                | 12 | 1 |
                | 11 | 1 |
                | 11 | 2 |
                """);

        run(fetchTool(), "{\"path\":\"/attachments/8.md\"}");

        // Stream 11 first, as it came first, with its events in the order they are stored.
        assertThat(read("/outputs/events.xml").lines()
                .filter(line -> line.contains("<stroom-event "))
                .map(line -> line.substring(0, line.indexOf('>') + 1).strip())
                .toList())
                .containsExactly(
                        "<stroom-event streamId=\"11\" eventId=\"1\">",
                        "<stroom-event streamId=\"11\" eventId=\"2\">",
                        "<stroom-event streamId=\"11\" eventId=\"3\">",
                        "<stroom-event streamId=\"12\" eventId=\"1\">");
    }

    @Test
    void fetchEventsNeedsPermissionToViewData() {
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);

        assertThat(run(fetchTool(), "{\"path\":\"/attachments/5.md\"}"))
                .startsWith("Error")
                .contains("permission to view data");
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void fetchEventsNeedsStreamAndEventIds() {
        attach("/attachments/6.md", "| User | Count |\n| --- | --- |\n| alice | 3 |\n");

        assertThat(run(fetchTool(), "{\"path\":\"/attachments/6.md\"}"))
                .startsWith("Error")
                .contains("no StreamId and EventId columns");
    }

    @Test
    void fetchEventsKeepsToTheLimits() {
        assertThat(run(fetchTool(), "{\"path\":\"/attachments/5.md\",\"limit\":2}"))
                .contains("Wrote 2 event(s)")
                .contains("3 more row(s) were left out");

        // An event over the size limit is left out.
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, 120),
                null, null, null, null, null, null, null);
        assertThat(run(fetchTool(), "{\"path\":\"/attachments/5.md\",\"out\":\"/outputs/small.xml\"}"))
                .contains("too large to fetch");
    }

    @Test
    void fetchEventsKeepsAnythingButWellFormedXmlAsText() {
        streams.put(11L, List.of(HEADER, "<Event><unclosed></Event>", "<!DOCTYPE x [<!ENTITY e SYSTEM "
                                                                       + "\"file:///etc/passwd\">]><x>&e;</x>",
                "<Event/>", FOOTER));

        run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        final String file = read("/outputs/events.xml");
        assertThat(file)
                .contains("eventId=\"1\" format=\"text\">")
                .contains("&lt;Event&gt;&lt;unclosed&gt;")
                .contains("eventId=\"2\" format=\"text\">")
                .doesNotContain("root:");
        // The file as a whole is still XML the tools can read.
        assertThat(run(new SearchEventsTool(workspace), "{\"path\":\"/outputs/events.xml\",\"text\":\"ENTITY\"}"))
                .contains("1 of 4 event(s) matched");
    }

    @Test
    void searchEventsGetsAValueFromEachEventAlone() {
        run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        final String result = run(new SearchEventsTool(workspace), """
                {"path":"/outputs/events.xml","xpath":"//d:Outcome/d:Reason","column":"reason"}""");

        assertThat(result).contains("Searched 4 event(s)").contains("Wrote 4 row(s)");
        // Each event's own value only, even with //; grouped by stream, as they were read.
        assertThat(read("/outputs/search_events.csv")).isEqualTo("""
                StreamId,EventId,reason\r
                11,1,\r
                11,2,Bad password\r
                11,3,\r
                12,1,Account locked\r
                """);
    }

    @Test
    void searchEventsFindsText() {
        run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        assertThat(run(new SearchEventsTool(workspace), """
                {"path":"/outputs/events.xml","text":"failure"}"""))
                .contains("2 of 4 event(s) matched");
        assertThat(read("/outputs/search_events.csv"))
                .startsWith("StreamId,EventId,match")
                .contains("11,2,")
                .contains("12,1,")
                .doesNotContain("11,1,");
        // Text in the xpath's value, with the value as the result.
        run(new SearchEventsTool(workspace), """
                {"path":"/outputs/events.xml","xpath":"//d:HostName","text":"^fw1$","regex":true,
                 "out":"/outputs/fw1.csv"}""");
        assertThat(read("/outputs/fw1.csv")).isEqualTo("StreamId,EventId,value\r\n11,1,fw1\r\n11,3,fw1\r\n");
    }

    @Test
    void searchEventsJoinsTheResultToTheTable() {
        run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        run(new SearchEventsTool(workspace), """
                {"path":"/outputs/events.xml","xpath":"//d:Outcome/d:Success","column":"success",
                 "join":"/attachments/5.md"}""");

        assertThat(read("/outputs/search_events.csv")).isEqualTo("""
                User,StreamId,EventId,success\r
                alice,11,1,Success\r
                bob,11,2,Failure\r
                bob,11,2,Failure\r
                carol,12,1,Failure\r
                dave,99,1,\r
                erin,11,3,Success\r
                """);
        // So the table tool can count it.
        assertThat(run(new TableTool(workspace, Mockito.mock(ColumnSummariser.class)),
                "{\"path\":\"/outputs/search_events.csv\",\"groupBy\":[\"success\"]}"))
                .contains("| Failure | 3 |");
    }

    @Test
    void searchEventsNeedsSomethingToLookFor() {
        run(fetchTool(), "{\"path\":\"/attachments/5.md\"}");

        assertThat(run(new SearchEventsTool(workspace), "{\"path\":\"/outputs/events.xml\"}"))
                .startsWith("Error").contains("Give xpath, text, or both");
        assertThat(run(new SearchEventsTool(workspace), "{\"path\":\"/attachments/5.md\",\"text\":\"x\"}"))
                .startsWith("Error");
        // Functions that read outside the event are refused.
        assertThat(run(new SearchEventsTool(workspace), """
                {"path":"/outputs/events.xml","xpath":"doc('file:///etc/passwd')"}"""))
                .startsWith("Error");
    }

    private FetchEventsTool fetchTool() {
        return new FetchEventsTool(workspace, () -> store, () -> securityContext);
    }

    private Source source(final long streamId) throws IOException {
        final List<String> segments = streams.get(streamId);
        if (segments == null) {
            throw new DataException("Unable to find meta data");
        }
        final InputStreamProvider provider = Mockito.mock(InputStreamProvider.class);
        Mockito.when(provider.get()).thenAnswer(invocation -> new FakeSegmentInputStream(segments));
        final Source source = Mockito.mock(Source.class);
        Mockito.when(source.get(0)).thenReturn(provider);
        return source;
    }

    private static String event(final String host, final String action, final String outcome, final String reason) {
        return "<Event><EventSource><Device><HostName>" + host + "</HostName></Device></EventSource>"
               + "<EventDetail><Authenticate><Action>" + action + "</Action><Outcome><Success>" + outcome
               + "</Success>" + (reason == null
                ? ""
                : "<Reason>" + reason + "</Reason>")
               + "</Outcome></Authenticate></EventDetail></Event>\n";
    }

    private void attach(final String path, final String content) {
        workspaceStore.createFile(chatId, AiAttachmentType.WORKSPACE_FILE, path, null, content);
    }

    private String read(final String path) {
        try {
            return Files.readString(workspace.localFile(chatId, WorkspacePath.parse(path)));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String run(final BuiltInTool tool, final String json) {
        return tool.execute(ToolExecutionRequest.builder().id("x").name("t").arguments(json).build(), chatId);
    }

    /// A segmented stream held in memory: reads the included segments, or all of them if none are.
    private static class FakeSegmentInputStream extends SegmentInputStream {

        private final List<String> segments;
        private final Set<Long> included = new TreeSet<>();
        private byte[] bytes;
        private int position;

        FakeSegmentInputStream(final List<String> segments) {
            this.segments = segments;
        }

        @Override
        public long count() {
            return segments.size();
        }

        @Override
        public void include(final long segment) {
            included.add(segment);
        }

        @Override
        public void includeAll() {
            included.clear();
        }

        @Override
        public void exclude(final long segment) {
            included.remove(segment);
        }

        @Override
        public void excludeAll() {
            included.clear();
        }

        @Override
        public long size() {
            return bytes().length;
        }

        @Override
        public int read() {
            final byte[] all = bytes();
            return position < all.length
                    ? all[position++] & 0xFF
                    : -1;
        }

        private byte[] bytes() {
            if (bytes == null) {
                final List<String> parts = new ArrayList<>();
                for (int i = 0; i < segments.size(); i++) {
                    if (included.isEmpty() || included.contains((long) i)) {
                        parts.add(segments.get(i));
                    }
                }
                bytes = String.join("", parts).getBytes(StandardCharsets.UTF_8);
            }
            return bytes;
        }
    }
}
