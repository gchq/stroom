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

package stroom.ai.impl;

import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiMemoryScope;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.User;
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
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiRetention {

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
    private AiMemoryDao memoryDao;
    @Inject
    private AiWorkspaceDao workspaceDao;
    @Inject
    private AiChatRunDao runDao;
    @Inject
    private WorkspaceStore workspaceStore;
    @Inject
    private AiAttachmentFileStore fileStore;
    @Inject
    private AiDbConnProvider aiDbConnProvider;

    private final UserService userService = Mockito.mock(UserService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private AiRetention retention;

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
                        bind(AiConfig.class).toInstance(new AiConfig());
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any());
        // Every user still exists unless a test says otherwise.
        Mockito.when(userService.loadByUuid(Mockito.anyString())).thenReturn(Optional.of(Mockito.mock(User.class)));
        retention = new AiRetention(aiDao, memoryDao, workspaceDao, runDao, workspaceStore, fileStore,
                Mockito.mock(McpClientCache.class), AiConfig::new, userService, securityContext);
    }

    @Test
    void testExpireFiles() {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final int expired = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/a",
                null, "a\n");
        final int current = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/b",
                null, "b\n");
        workspaceDao.setRetention(expired, null, System.currentTimeMillis() - 1);

        assertThat(retention.expireFiles(System.currentTimeMillis())).isEqualTo(1);

        assertThat(workspaceDao.getFileInfo(expired).orElseThrow().status())
                .isEqualTo(AiAttachmentStatus.UNAVAILABLE);
        assertThat(workspaceDao.getFileInfo(current).orElseThrow().status()).isEqualTo(AiAttachmentStatus.READY);
        assertThat(fileStore.exists(expired)).isFalse();
        assertThat(fileStore.exists(current)).isTrue();
    }

    @Test
    void testDeleteOldChats() throws SQLException {
        final AiChat old = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat oldButRunning = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChat recent = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final int oldFile = workspaceStore.createFile(old.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/a",
                null, "a\n");
        runDao.create(oldButRunning.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);
        final long later = System.currentTimeMillis() + Duration.ofDays(91).toMillis();
        // Touch the recent chat "later".
        setUpdateTime(recent.getId(), later);

        assertThat(retention.deleteOldChats(later)).isEqualTo(1);

        assertThat(aiDao.getChat(old.getId())).isEmpty();
        assertThat(aiDao.getChat(oldButRunning.getId())).isPresent();
        assertThat(aiDao.getChat(recent.getId())).isPresent();
        assertThat(fileStore.exists(oldFile)).isFalse();
    }

    private void setUpdateTime(final int chatId, final long timeMs) throws SQLException {
        try (final Connection connection = aiDbConnProvider.getConnection();
                final PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ai_chat SET update_time_ms = ? WHERE id = ?")) {
            statement.setLong(1, timeMs);
            statement.setInt(2, chatId);
            statement.executeUpdate();
        }
    }

    @Test
    void testNoChatRetention() {
        retention = new AiRetention(aiDao, memoryDao, workspaceDao, runDao, workspaceStore, fileStore,
                Mockito.mock(McpClientCache.class),
                () -> new AiConfig(null, null, null, null, null, null, null, null, StroomDuration.ZERO),
                userService, securityContext);
        aiDao.createChat(UserRef.builder().uuid("user1").build());

        assertThat(retention.deleteOldChats(System.currentTimeMillis() + Duration.ofDays(1000).toMillis()))
                .isZero();
    }

    @Test
    void testDeleteDataOfRemovedUsers() {
        final AiChat gone = aiDao.createChat(UserRef.builder().uuid("gone").build());
        final AiChat kept = aiDao.createChat(UserRef.builder().uuid("kept").build());
        memoryDao.create("gone", AiMemoryScope.USER, "", "a", "x", "gone");
        memoryDao.create("kept", AiMemoryScope.USER, "", "a", "x", "kept");
        Mockito.when(userService.loadByUuid("gone")).thenReturn(Optional.empty());

        assertThat(retention.deleteDataOfRemovedUsers()).isEqualTo(1);

        assertThat(aiDao.getChat(gone.getId())).isEmpty();
        assertThat(aiDao.getChat(kept.getId())).isPresent();
        assertThat(memoryDao.getUserUuids()).containsExactly("kept");
    }

    @Test
    void testDeleteStaleLocalCopies() throws IOException {
        final AiChat chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final int fileId = workspaceStore.createFile(chat.getId(), AiAttachmentType.TOOL_OUTPUT, "/outputs/a",
                null, "a\n");
        // A local copy of a file that no longer exists, e.g. its chat was deleted on another node.
        final Path stale = fileStore.createAttachmentFile(999_999);
        Files.writeString(stale, "old");
        final Path other = fileStore.getAttachmentDir().resolve("not-a-workspace-file.txt");
        Files.writeString(other, "keep");

        assertThat(retention.deleteStaleLocalCopies()).isEqualTo(1);

        assertThat(stale).doesNotExist();
        assertThat(fileStore.exists(fileId)).isTrue();
        assertThat(other).exists();
    }

    @Test
    void testExecRunsAsTheProcessingUser() {
        retention.exec();

        Mockito.verify(securityContext).asProcessingUser(Mockito.any());
    }
}
