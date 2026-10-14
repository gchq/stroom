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

import stroom.ai.impl.AiAttachmentFileStore;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.AiWorkspaceDao.FileInfo;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestWorkspaceStore {

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
    private AiDbConnProvider aiDbConnProvider;
    @Inject
    private WorkspaceStore workspaceStore;

    private AiChat chat;
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private AiConfig aiConfig = new AiConfig();

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
                        bind(AiConfig.class).toProvider(() -> aiConfig);
                    }
                });
        injector.injectMembers(this);

        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
    }

    @Test
    void testCreateAndReadLines() {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/a.txt",
                null, "line 0\nline 1\nline 2\n");

        assertThat(workspaceStore.lineCount(fileId)).isEqualTo(3);
        assertThat(workspaceStore.readLines(fileId, 1, 5)).containsExactly("line 1", "line 2");
        assertThat(workspaceStore.readLines(fileId, 5, 5)).isEmpty();
        assertThat(workspaceDao.findFile(chat.getId(), "/outputs/a.txt")).contains(fileId);
        assertThat(workspaceDao.getTotalSize(chat.getId())).isEqualTo(21);
    }

    @Test
    void testLargeFileIsChunkedAndRangesReadAcrossChunks() {
        // About 2.5 chunks.
        final int lines = 40_000;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            sb.append(String.format("%08d some text to fill out the line a bit more", i)).append('\n');
        }
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/b.txt",
                null, sb.toString());

        assertThat(workspaceDao.getChunkInfo(fileId)).hasSizeGreaterThan(1);
        assertThat(workspaceStore.lineCount(fileId)).isEqualTo(lines);
        // A range that crosses a chunk boundary.
        final long firstLineOfSecondChunk = workspaceDao.getChunkInfo(fileId).get(1).firstLine();
        final List<String> read = workspaceStore.readLines(fileId, firstLineOfSecondChunk - 2, 4);
        assertThat(read).hasSize(4);
        assertThat(read.getFirst()).startsWith(String.format("%08d", firstLineOfSecondChunk - 2));
        assertThat(read.getLast()).startsWith(String.format("%08d", firstLineOfSecondChunk + 1));
    }

    @Test
    void testLocalCopyIsRebuiltWhenMissing() throws IOException {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.WORKSPACE_FILE,
                "/attachments/1.profile.md", null, "| a |\n| --- |\n| 1 |\n");
        final Path localFile = workspaceStore.localFile(fileId);
        // As if on another node, or after a restart.
        Files.delete(localFile);

        assertThat(workspaceStore.localFile(fileId)).hasContent("| a |\n| --- |\n| 1 |");
    }

    @Test
    void testPersistReplacesContent() throws IOException {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/c.txt",
                null, "old\n");
        final Path newContent = tempDir.resolve("new.txt");
        Files.writeString(newContent, "new 1\nnew 2\n");

        workspaceStore.persist(fileId, "/outputs/c.txt", newContent);

        assertThat(workspaceStore.readLines(fileId, 0, 10)).containsExactly("new 1", "new 2");
    }

    @Test
    void testDeletingAttachmentsAndChatsDeletesContent() {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/d.txt",
                null, "x\n");
        workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/e.txt", null, "y\n");

        aiDao.deleteAttachment(fileId);
        assertThat(workspaceDao.getChunkInfo(fileId)).isEmpty();

        // The chunks' foreign key must not stop the chat being deleted.
        aiDao.deleteChat(chat.getId());
        assertThat(workspaceDao.getTotalSize(chat.getId())).isZero();
    }

    @Test
    void testFilesExpire() {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/f.txt",
                null, "x\n");
        final FileInfo info = workspaceDao.getFileInfo(fileId).orElseThrow();
        // The default retention is 7 days.
        assertThat(info.expireTimeMs()).isBetween(
                System.currentTimeMillis() + Duration.ofDays(7).toMillis() - 60_000,
                System.currentTimeMillis() + Duration.ofDays(7).toMillis());
        assertThat(workspaceDao.findExpired(System.currentTimeMillis(), 10)).isEmpty();
        assertThat(workspaceDao.findExpired(info.expireTimeMs(), 10)).containsExactly(fileId);

        // Expire it.
        workspaceDao.setRetention(fileId, null, System.currentTimeMillis() - 1);

        assertThatThrownBy(() -> workspaceStore.readLines(fileId, 0, 10))
                .isInstanceOf(WorkspaceFileUnavailableException.class)
                .hasMessageContaining("/outputs/f.txt")
                .hasMessageContaining("retention age");
        assertThat(workspaceDao.getChunkInfo(fileId)).isEmpty();
        assertThat(workspaceDao.getFileInfo(fileId).orElseThrow().status())
                .isEqualTo(AiAttachmentStatus.UNAVAILABLE);
        assertThat(workspaceDao.findAvailable(List.of(fileId))).isEmpty();
        // And it stays unavailable.
        assertThatThrownBy(() -> workspaceStore.localFile(fileId))
                .isInstanceOf(WorkspaceFileUnavailableException.class);
    }

    @Test
    void testNoRetention() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, StroomDuration.ZERO, null, null, null);
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/f.txt",
                null, "x\n");

        assertThat(workspaceDao.getFileInfo(fileId).orElseThrow().expireTimeMs()).isNull();
    }

    @Test
    void testAccessToTheSourceIsCheckedAgain() {
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/f.txt",
                null, "x\n");
        workspaceStore.setRetention(fileId, "source-uuid");
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.USE)))
                .thenReturn(true);

        assertThat(workspaceStore.readLines(fileId, 0, 10)).containsExactly("x");
        final ArgumentCaptor<DocRef> docRef = ArgumentCaptor.forClass(DocRef.class);
        Mockito.verify(securityContext).hasDocumentPermission(docRef.capture(), Mockito.eq(DocumentPermission.USE));
        assertThat(docRef.getValue().getUuid()).isEqualTo("source-uuid");

        // The user loses access.
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.USE)))
                .thenReturn(false);

        assertThatThrownBy(() -> workspaceStore.readLines(fileId, 0, 10))
                .isInstanceOf(WorkspaceFileUnavailableException.class)
                .hasMessageContaining("no longer have access");
        assertThat(workspaceDao.getChunkInfo(fileId)).isEmpty();
    }
}
