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
import stroom.ai.api.TableSummariser;
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
import stroom.data.store.api.Store;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkspaceTools {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");

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
    private final SecurityContext injectorSecurityContext = Mockito.mock(SecurityContext.class);
    private AiConfig aiConfig = new AiConfig();
    private int chatId;
    private int otherChatId;

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
                        bind(SecurityContext.class).toInstance(injectorSecurityContext);
                        bind(AiService.class).toInstance(aiService);
                        bind(AiConfig.class).toProvider(() -> aiConfig);
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        chatId = chat.getId();
        otherChatId = otherChat.getId();
        Mockito.when(aiService.getChat(chatId)).thenReturn(chat);
        Mockito.when(aiService.getChat(otherChatId)).thenReturn(otherChat);

        final StringBuilder events = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            events.append(i % 3 == 0
                    ? "ERROR"
                    : "INFO").append(" event ").append(i).append(" host=fw").append(i % 2).append('\n');
        }
        workspace.write(chatId, WorkspacePath.parse("/outputs/events.log"), events.toString(), null);
        workspaceStore.createFile(chatId, AiAttachmentType.WORKSPACE_FILE, "/attachments/7.md", null,
                "| host | n |\n| --- | --- |\n| fw1 | 3 |\n");
    }

    /// Every workspace tool, given each path or output argument an attacker might try, refuses it: nothing
    /// outside the chat's workspace is read, written or deleted, whatever the model asks for.
    @ParameterizedTest
    @ValueSource(strings = {
            "SECRET",
            "file://SECRET",
            "/outputs/../../../../../../SECRET",
            "/outputs/%2e%2e/%2e%2e/etc/passwd",
            "/outputs/..\\..\\etc\\passwd",
            "../outputs/x",
            "/etc/passwd",
            "~/.ssh/id_rsa",
            "/outputs/./x",
            "/outputs/x\u0000.txt",
            "/proc/self/environ"})
    void noToolReachesOutsideTheWorkspace(final String attempt) throws IOException {
        final Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-7f3a");
        final String path = JsonUtil.writeValueAsString(attempt.replace("SECRET", secret.toAbsolutePath().toString()),
                false);
        final TableTool tableTool = tableTool();
        Mockito.when(injectorSecurityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
        final Store store = Mockito.mock(Store.class);
        final FetchEventsTool fetchEvents = new FetchEventsTool(workspace, () -> store, () -> injectorSecurityContext);
        final List<String> calls = List.of(
                run(new LsTool(workspace), "{\"path\":" + path + "}"),
                run(new ReadFileTool(workspace), "{\"path\":" + path + "}"),
                run(new WcTool(workspace), "{\"path\":" + path + "}"),
                run(new GrepTool(workspace), "{\"path\":" + path + ",\"pattern\":\"SECRET\"}"),
                run(new GrepTool(workspace), "{\"path\":\"/outputs/events.log\",\"pattern\":\"ERROR\","
                                             + "\"out\":" + path + "}"),
                run(new JqTool(workspace), "{\"path\":" + path + ",\"query\":\".\"}"),
                run(new XPathTool(workspace), "{\"path\":" + path + ",\"expression\":\"/\"}"),
                run(tableTool, "{\"path\":" + path + "}"),
                run(tableTool, "{\"path\":\"/attachments/7.md\",\"out\":" + path + "}"),
                run(new WriteFileTool(workspace), "{\"path\":" + path + ",\"content\":\"planted\"}"),
                run(new StrReplaceTool(workspace), "{\"path\":" + path + ",\"oldText\":\"TOP\","
                                                   + "\"newText\":\"X\"}"),
                run(new DeleteFileTool(workspace), "{\"path\":" + path + "}"),
                run(fetchEvents, "{\"path\":" + path + "}"),
                run(fetchEvents, "{\"path\":\"/attachments/7.md\",\"out\":" + path + "}"),
                run(new SearchEventsTool(workspace), "{\"path\":" + path + ",\"text\":\"SECRET\"}"),
                run(new SearchEventsTool(workspace), "{\"path\":\"/outputs/events.log\",\"text\":\"x\","
                                                     + "\"out\":" + path + "}"),
                run(new SearchEventsTool(workspace), "{\"path\":\"/outputs/events.log\",\"text\":\"x\","
                                                     + "\"join\":" + path + "}"));

        assertThat(calls).allSatisfy(result -> assertThat(result)
                .startsWith("Error")
                .doesNotContain("TOP-SECRET-7f3a"));
        // Nothing was changed outside the workspace.
        assertThat(Files.readString(secret)).isEqualTo("TOP-SECRET-7f3a");
        Mockito.verifyNoInteractions(store);
        assertThat(tempDir.resolve("x")).doesNotExist();
    }

    @Test
    void testLs() {
        assertThat(run(new LsTool(workspace), "{}"))
                .contains("/attachments/7.md")
                .contains("/outputs/events.log");
        assertThat(run(new LsTool(workspace), "{\"path\":\"/attachments\"}"))
                .contains("/attachments/7.md")
                .doesNotContain("events.log");
        assertThat(new LsTool(workspace).execute(request("{}"), otherChatId)).startsWith("There are no files");
    }

    @Test
    void testReadFile() {
        final ReadFileTool tool = new ReadFileTool(workspace);

        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"lineCount\":2}"))
                .startsWith("/outputs/events.log (lines 1-2 of 300):")
                .contains("     1\tINFO event 1 host=fw1")
                .contains("     2\tINFO event 2 host=fw0")
                .contains("(298 more lines; read on from line 3)");
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"startLine\":299}"))
                .contains("lines 299-300 of 300")
                .doesNotContain("more lines");
        // Tail.
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"startLine\":-1}"))
                .contains("   300\tERROR event 300");
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"startLine\":500}"))
                .contains("nothing from line 500");
        assertThat(run(tool, "{\"path\":\"/attachments/7.md\"}")).contains("| fw1 | 3 |");
    }

    @Test
    void testReadFileLimits() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(null, null, null, 10, 300, null, null, null, null, null), null, null, null, null,
                        null, null, null, null);
        workspace.write(chatId, WorkspacePath.parse("/outputs/long.txt"), "x".repeat(5_000) + "\n", null);

        assertThat(run(new ReadFileTool(workspace), "{\"path\":\"/outputs/events.log\",\"lineCount\":100}"))
                .contains("lines 1-")
                .hasSizeLessThan(500);
        assertThat(run(new ReadFileTool(workspace), "{\"path\":\"/outputs/events.log\",\"lineCount\":100}")
                .lines()
                .filter(line -> line.matches("\\s*\\d+\\t.*"))
                .count()).isLessThanOrEqualTo(10);
        aiConfig = new AiConfig();
        assertThat(run(new ReadFileTool(workspace), "{\"path\":\"/outputs/long.txt\"}"))
                .contains("… (3000 more characters)");
    }

    @Test
    void testPathsAreConfined() {
        final ReadFileTool tool = new ReadFileTool(workspace);

        assertThat(run(tool, "{\"path\":\"/outputs/../attachments/7.md\"}")).startsWith("Error:");
        assertThat(run(tool, "{\"path\":\"/etc/passwd\"}")).startsWith("Error:");
        assertThat(run(tool, "{\"path\":\"../../../../etc/passwd\"}")).startsWith("Error:");
        assertThat(run(tool, "{\"path\":\"/outputs/%2e%2e/x\"}")).startsWith("Error:");
        assertThat(run(tool, "{}")).startsWith("Error:");
        // Another chat's files cannot be reached.
        assertThat(tool.execute(request("{\"path\":\"/outputs/events.log\"}"), otherChatId))
                .startsWith("Error: There is no file");
        assertThat(tool.execute(request("{\"path\":\"/outputs/events.log\"}"))).startsWith("Error:");
    }

    @Test
    void testGrep() {
        final GrepTool tool = new GrepTool(workspace);

        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"pattern\":\"event 30 \"}"))
                .isEqualTo("30: ERROR event 30 host=fw0");
        // Literal by default.
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"pattern\":\"event .0 \"}"))
                .isEqualTo("No lines match.");
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"pattern\":\"event [12]0 \","
                             + "\"regex\":true}"))
                .contains("10: INFO event 10")
                .contains("20: INFO event 20");
        assertThat(run(tool, "{\"path\":\"/outputs/events.log\",\"pattern\":\"error\",\"ignoreCase\":true,"
                             + "\"maxResults\":3}"))
                .contains("3: ERROR")
                .contains("(100 matching lines in all; 97 not shown.");
        // A directory searches every file in it.
        assertThat(run(tool, "{\"path\":\"/\",\"pattern\":\"fw1 |\"}")).isEqualTo("/attachments/7.md:3: | fw1 | 3 |");
    }

    @Test
    void testGrepToAFile() {
        assertThat(run(new GrepTool(workspace), "{\"path\":\"/outputs/events.log\",\"pattern\":\"ERROR\","
                                               + "\"out\":\"/outputs/errors.log\"}"))
                .isEqualTo("Wrote 100 matching line(s) to /outputs/errors.log.");
        assertThat(workspace.lineCount(chatId, WorkspacePath.parse("/outputs/errors.log"))).isEqualTo(100);
        assertThat(run(new GrepTool(workspace), "{\"path\":\"/outputs/events.log\",\"pattern\":\"ERROR\","
                                               + "\"out\":\"/attachments/x\"}"))
                .contains("read-only");
    }

    @Test
    void testGrepReDoSIsStopped() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(null, null, null, null, null, StroomDuration.ofMillis(300), null, null,
                        null, null), null,
                null,
                null,
                null, null, null, null, null);
        workspace.write(chatId, WorkspacePath.parse("/outputs/csv.txt"), "a,".repeat(40) + "\n", null);

        assertThat(run(new GrepTool(workspace), "{\"path\":\"/outputs/csv.txt\",\"pattern\":\"(.*?,){25}x\","
                                               + "\"regex\":true}"))
                .startsWith("Error: This took too long");
    }

    @Test
    void testWc() {
        assertThat(run(new WcTool(workspace), "{\"path\":\"/outputs/events.log\"}"))
                .startsWith("/outputs/events.log: 300 lines, ");
    }

    @Test
    void testWriteReplaceAndDelete() {
        assertThat(run(new WriteFileTool(workspace), "{\"path\":\"/outputs/notes.md\",\"content\":\"# Notes\\n"
                                                    + "fw1 is noisy\\n\"}"))
                .isEqualTo("Wrote /outputs/notes.md (2 lines).");
        assertThat(run(new StrReplaceTool(workspace), "{\"path\":\"/outputs/notes.md\",\"oldText\":\"noisy\","
                                                     + "\"newText\":\"quiet\"}"))
                .isEqualTo("Replaced the text in /outputs/notes.md.");
        assertThat(workspace.readLines(chatId, WorkspacePath.parse("/outputs/notes.md"), 0, 10))
                .containsExactly("# Notes", "fw1 is quiet");
        assertThat(run(new StrReplaceTool(workspace), "{\"path\":\"/outputs/notes.md\",\"oldText\":\"x\","
                                                     + "\"newText\":\"y\"}"))
                .contains("is not in");
        assertThat(run(new StrReplaceTool(workspace), "{\"path\":\"/outputs/events.log\",\"oldText\":\"INFO\","
                                                     + "\"newText\":\"y\"}"))
                .contains("more than once");
        assertThat(run(new DeleteFileTool(workspace), "{\"path\":\"/outputs/notes.md\"}"))
                .isEqualTo("Deleted /outputs/notes.md.");
        assertThat(run(new WcTool(workspace), "{\"path\":\"/outputs/notes.md\"}")).contains("There is no file");
    }

    @Test
    void testAttachmentsAndMemoryCannotBeWritten() {
        assertThat(run(new WriteFileTool(workspace), "{\"path\":\"/attachments/7.md\",\"content\":\"x\"}"))
                .contains("read-only");
        assertThat(run(new StrReplaceTool(workspace), "{\"path\":\"/attachments/7.md\",\"oldText\":\"fw1\","
                                                     + "\"newText\":\"y\"}"))
                .contains("read-only");
        assertThat(run(new DeleteFileTool(workspace), "{\"path\":\"/attachments/7.md\"}")).contains("read-only");
        assertThat(run(new WriteFileTool(workspace), "{\"path\":\"/memories/a\",\"content\":\"x\"}"))
                .contains("memory tool");
    }

    @Test
    void testQuota() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(100L, null, null, null, null, null, null, null, null, null),
                        null, null, null, null, null,
                        null, null, null);

        assertThat(run(new WriteFileTool(workspace), "{\"path\":\"/outputs/big\",\"content\":\""
                                                    + "x".repeat(200) + "\"}"))
                .contains("may be at most 100 bytes");
    }

    @Test
    void testJq() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/events.json"),
                "{\"host\":\"fw1\",\"n\":3}\n{\"host\":\"fw2\",\"n\":5}\n", null);

        assertThat(run(new JqTool(workspace), "{\"path\":\"/outputs/events.json\",\"query\":\".host\"}"))
                .isEqualTo("\"fw1\"\n\"fw2\"");
        assertThat(run(new JqTool(workspace), "{\"path\":\"/outputs/events.json\",\"query\":\".host\","
                                             + "\"raw\":true,\"out\":\"/outputs/hosts.txt\"}"))
                .isEqualTo("Wrote 2 result(s) to /outputs/hosts.txt.");
        assertThat(workspace.readLines(chatId, WorkspacePath.parse("/outputs/hosts.txt"), 0, 5))
                .containsExactly("fw1", "fw2");
        assertThat(run(new JqTool(workspace), "{\"path\":\"/outputs/events.json\",\"query\":\"env\"}"))
                .startsWith("Error:");
        assertThat(run(new JqTool(workspace), "{\"path\":\"/outputs/events.log\",\"query\":\".\"}"))
                .startsWith("Error: /outputs/events.log is not JSON");
    }

    @Test
    void testXPath() {
        workspace.write(chatId, WorkspacePath.parse("/outputs/events.xml"), """
                <Events xmlns="event-logging:3">
                  <Event><EventSource><Device><HostName>fw1</HostName></Device></EventSource></Event>
                </Events>
                """, null);

        // The document's namespace is bound to 'd'.
        assertThat(run(new XPathTool(workspace), "{\"path\":\"/outputs/events.xml\","
                                                + "\"expression\":\"//d:HostName\"}"))
                .isEqualTo("fw1");
        assertThat(run(new XPathTool(workspace), "{\"path\":\"/outputs/events.xml\",\"expression\":\"//e:HostName\","
                                                + "\"namespaces\":{\"e\":\"event-logging:3\"}}"))
                .isEqualTo("fw1");
        assertThat(run(new XPathTool(workspace), "{\"path\":\"/outputs/events.xml\","
                                                + "\"expression\":\"doc('file:///etc/passwd')\"}"))
                .startsWith("Error:");
    }

    @Test
    void testTable() {
        assertThat(run(tableTool(), "{\"path\":\"/attachments/7.md\",\"groupBy\":[\"host\"],"
                                                + "\"aggregates\":[{\"function\":\"sum\",\"column\":\"n\"}]}"))
                .startsWith("1 of 1 rows matched; the result has 1 row(s).")
                .contains("| host | sum(n) |")
                .contains("| fw1 | 3 |");
        assertThat(run(tableTool(), "{\"path\":\"/attachments/7.md\",\"where\":[{\"column\":\"x\","
                                                + "\"op\":\"=\",\"value\":\"1\"}]}"))
                .startsWith("Error: There is no column 'x'");
        assertThat(run(tableTool(), "{\"path\":\"/attachments/7.md\",\"where\":\"host = fw1\"}"))
                .startsWith("Error: where must be a list of objects.");
    }

    @Test
    void testResultsKeepTheirSource() {
        final int attachment = workspaceStore.createFile(chatId, AiAttachmentType.WORKSPACE_FILE,
                "/attachments/8.md", null, "| host |\n| --- |\n| fw1 |\n");
        workspaceStore.setRetention(attachment, "index-uuid");
        final SecurityContext securityContext = injectorSecurityContext;
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(true);

        assertThat(run(tableTool(), "{\"path\":\"/attachments/8.md\",\"distinct\":true,"
                                                + "\"out\":\"/outputs/hosts.csv\"}"))
                .startsWith("Wrote the result to /outputs/hosts.csv.");
        assertThat(workspace.readLines(chatId, WorkspacePath.parse("/outputs/hosts.csv"), 0, 5))
                .containsExactly("host,count", "fw1,1");
        assertThat(workspace.sourceDocUuid(chatId, WorkspacePath.parse("/outputs/hosts.csv")))
                .isEqualTo("index-uuid");

        // If the user loses access to the source, results made from it go too.
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);
        assertThat(run(new ReadFileTool(workspace), "{\"path\":\"/outputs/hosts.csv\"}"))
                .contains("no longer have access");
    }

    @Test
    void testRegistration() {
        final BuiltInTools tools = new BuiltInTools(Set.of(new LsTool(workspace), new ReadFileTool(workspace),
                new GrepTool(workspace), new WcTool(workspace), new WriteFileTool(workspace),
                new StrReplaceTool(workspace), new DeleteFileTool(workspace)));

        assertThat(tools.toolMap().keySet()).extracting(spec -> spec.name())
                .containsExactlyInAnyOrder("ls", "read_file", "grep", "wc", "write_file", "str_replace",
                        "delete_file");
        assertThat(new JqTool(workspace).boundsItsOutput()).isTrue();
        assertThat(tableTool().readsUntrustedContent()).isTrue();
        // Reading files reads data that may hold planted instructions; writing does not.
        assertThat(tools.readsUntrustedContent("read_file")).isTrue();
        assertThat(tools.readsUntrustedContent("grep")).isTrue();
        assertThat(tools.readsUntrustedContent("write_file")).isFalse();
        // Tools that change the workspace are not offered in Ask and Plan modes.
        assertThat(tools.isReadOnly("write_file")).isFalse();
        assertThat(tools.isReadOnly("str_replace")).isFalse();
        assertThat(tools.isReadOnly("delete_file")).isFalse();
        assertThat(tools.isReadOnly("read_file")).isTrue();
        assertThat(tools.boundsItsOutput("read_file")).isTrue();
    }

    private TableTool tableTool() {
        return new TableTool(workspace, new ColumnSummariser(workspace, Mockito.mock(TableSummariser.class),
                () -> null));
    }

    private String run(final BuiltInTool tool, final String arguments) {
        return tool.execute(request(arguments), chatId);
    }

    private static ToolExecutionRequest request(final String arguments) {
        return ToolExecutionRequest.builder().id("x").name("t").arguments(arguments).build();
    }
}
