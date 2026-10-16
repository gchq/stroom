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

package stroom.ai.impl.workspace;

import stroom.ai.api.AiService;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestChatWorkspace {

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
    private AiWorkspaceDao workspaceDao;
    @Inject
    private WorkspaceStore workspaceStore;
    @Inject
    private AiDbConnProvider aiDbConnProvider;
    @Inject
    private ChatWorkspace workspace;

    private final AiService aiService = Mockito.mock(AiService.class);
    private AiConfig aiConfig = new AiConfig();
    private AiChat chat;
    private AiChat otherChat;

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
                        bind(SecurityContext.class).toInstance(Mockito.mock(SecurityContext.class));
                        bind(AiService.class).toInstance(aiService);
                        bind(AiConfig.class).toProvider(() -> aiConfig);
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        Mockito.when(aiService.getChat(chat.getId())).thenReturn(chat);
        Mockito.when(aiService.getChat(otherChat.getId())).thenReturn(otherChat);
    }

    @Test
    void testWriteReadListAndDelete() {
        final WorkspacePath path = WorkspacePath.parse("/outputs/errors/by-host.csv");
        workspace.write(chat.getId(), path, "host,count\nfw1,42\n", null);

        assertThat(workspace.readLines(chat.getId(), path, 0, 10)).containsExactly("host,count", "fw1,42");
        assertThat(workspace.lineCount(chat.getId(), path)).isEqualTo(2);
        assertThat(workspace.list(chat.getId(), WorkspacePath.parse("/outputs/")))
                .extracting(entry -> entry.path().path())
                .containsExactly("/outputs/errors/by-host.csv");
        assertThat(workspace.list(chat.getId(), WorkspacePath.parse("/attachments"))).isEmpty();

        // Writing again replaces it.
        workspace.write(chat.getId(), path, "host,count\n", null);
        assertThat(workspace.readLines(chat.getId(), path, 0, 10)).containsExactly("host,count");
        assertThat(workspace.list(chat.getId(), WorkspacePath.ROOT)).hasSize(1);

        workspace.delete(chat.getId(), path);
        assertThatThrownBy(() -> workspace.readLines(chat.getId(), path, 0, 10))
                .hasMessageContaining("There is no file");
    }

    @Test
    void testAChatOnlyReachesItsOwnWorkspace() {
        final WorkspacePath path = WorkspacePath.parse("/outputs/secret.txt");
        workspace.write(otherChat.getId(), path, "secret", null);

        assertThatThrownBy(() -> workspace.readLines(chat.getId(), path, 0, 10))
                .hasMessageContaining("There is no file");
        assertThat(workspace.list(chat.getId(), WorkspacePath.ROOT)).isEmpty();
        assertThatThrownBy(() -> workspace.delete(chat.getId(), path)).hasMessageContaining("There is no file");
    }

    @Test
    void testAreas() {
        final int attachment = workspaceStore.createFile(chat.getId(), AiAttachmentType.WORKSPACE_FILE,
                "/attachments/1.md", null, "| a |\n");
        final WorkspacePath attachmentPath = WorkspacePath.parse("/attachments/1.md");

        assertThat(workspace.readLines(chat.getId(), attachmentPath, 0, 1)).containsExactly("| a |");
        assertThatThrownBy(() -> workspace.write(chat.getId(), attachmentPath, "x", null))
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> workspace.delete(chat.getId(), attachmentPath))
                .hasMessageContaining("read-only");
        assertThatThrownBy(() -> workspace.write(chat.getId(), WorkspacePath.parse("/memories/a"), "x", null))
                .hasMessageContaining("memory tool");
        assertThatThrownBy(() -> workspace.write(chat.getId(), WorkspacePath.parse("/outputs/"), "x", null))
                .hasMessageContaining("not a file path");
        assertThatThrownBy(() -> workspace.readLines(chat.getId(), WorkspacePath.parse("/outputs"), 0, 1))
                .hasMessageContaining("is a directory");
        assertThat(workspaceDao.getFileInfo(attachment)).isPresent();
    }

    @Test
    void testLimits() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null,
                new AiWorkspaceConfig(10L, 15L, 18L, null, null, null, null, null), null, null, null, null,
                        null, null, null);
        final WorkspacePath a = WorkspacePath.parse("/outputs/a");

        assertThatThrownBy(() -> workspace.write(chat.getId(), a, line(11), null))
                .hasMessageContaining("at most 10 bytes");
        workspace.write(chat.getId(), a, line(10), null);
        // Replacing a file only counts the difference.
        workspace.write(chat.getId(), a, line(10), null);
        assertThatThrownBy(() -> workspace.write(chat.getId(), WorkspacePath.parse("/outputs/b"), line(6), null))
                .hasMessageContaining("may hold at most 15");
        workspace.write(otherChat.getId(), WorkspacePath.parse("/outputs/c"), line(8), null);
        // The user's other chats count too.
        assertThatThrownBy(() -> workspace.write(chat.getId(), WorkspacePath.parse("/outputs/b"), line(1), null))
                .hasMessageContaining("at most 18 in all");
    }

    /// @return A line of text of the given length, including its line end.
    private static String line(final int length) {
        return "x".repeat(length - 1) + "\n";
    }

    @Test
    void testUnavailableFilesAreListedWithTheReason() {
        final WorkspacePath path = WorkspacePath.parse("/outputs/old.txt");
        final int fileId = workspace.write(chat.getId(), path, "x", null);
        workspaceStore.makeUnavailable(fileId, "it reached its retention age");

        assertThat(workspace.list(chat.getId(), WorkspacePath.ROOT))
                .singleElement()
                .satisfies(entry -> assertThat(entry.unavailable()).isEqualTo("it reached its retention age"));
        assertThatThrownBy(() -> workspace.readLines(chat.getId(), path, 0, 1))
                .isInstanceOf(WorkspaceFileUnavailableException.class);
    }

    @Test
    void testALinkInPlaceOfALocalCopyIsNotFollowed() throws IOException {
        final WorkspacePath path = WorkspacePath.parse("/outputs/a.txt");
        final int fileId = workspace.write(chat.getId(), path, "stored", null);
        final Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "secret");
        final Path localCopy = workspace.localFile(chat.getId(), path);
        Files.delete(localCopy);
        Files.createSymbolicLink(localCopy, secret);

        final Path rebuilt = workspaceStore.localFile(fileId);

        assertThat(Files.isSymbolicLink(rebuilt)).isFalse();
        assertThat(rebuilt).hasContent("stored");
        assertThat(secret).hasContent("secret");
    }
}
