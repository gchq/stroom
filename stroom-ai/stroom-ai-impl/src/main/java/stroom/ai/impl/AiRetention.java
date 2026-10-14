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

import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.memory.MemoryChange;
import stroom.ai.impl.memory.MemoryChange.ApprovalStatus;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// The 'AI Chat Retention' job, which keeps 'Ask Stroom AI' data no longer than it should be kept:
///
/// - workspace files (which hold copies of Stroom data) past their retention age lose their content;
/// - chats not changed for longer than the chat retention age are deleted, with their files and memory;
/// - the chats and memory of users who have been deleted are deleted;
/// - this node's local copies of workspace files that are no longer held are deleted.
///
/// Chats with a run working on them are left until it finishes.
public class AiRetention {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiRetention.class);

    public static final String JOB_NAME = "AI Chat Retention";
    static final int BATCH_SIZE = 1_000;

    private final AiDao aiDao;
    private final AiMemoryDao memoryDao;
    private final AiWorkspaceDao workspaceDao;
    private final AiChatRunDao runDao;
    private final WorkspaceStore workspaceStore;
    private final AiAttachmentFileStore fileStore;
    private final McpClientCache mcpClientCache;
    private final Provider<AiConfig> aiConfigProvider;
    private final UserService userService;
    private final SecurityContext securityContext;
    private final ToolApprovalService toolApprovalService;

    @Inject
    AiRetention(final AiDao aiDao,
                final AiMemoryDao memoryDao,
                final AiWorkspaceDao workspaceDao,
                final AiChatRunDao runDao,
                final WorkspaceStore workspaceStore,
                final AiAttachmentFileStore fileStore,
                final McpClientCache mcpClientCache,
                final Provider<AiConfig> aiConfigProvider,
                final UserService userService,
                final SecurityContext securityContext,
                final ToolApprovalService toolApprovalService) {
        this.aiDao = aiDao;
        this.memoryDao = memoryDao;
        this.workspaceDao = workspaceDao;
        this.runDao = runDao;
        this.workspaceStore = workspaceStore;
        this.fileStore = fileStore;
        this.mcpClientCache = mcpClientCache;
        this.aiConfigProvider = aiConfigProvider;
        this.userService = userService;
        this.securityContext = securityContext;
        this.toolApprovalService = toolApprovalService;
    }

    /// Runs the job.
    public void exec() {
        securityContext.asProcessingUser(() -> {
            final long now = System.currentTimeMillis();
            final int files = expireFiles(now);
            final int chats = deleteOldChats(now);
            final int removedUsers = deleteDataOfRemovedUsers();
            final int localCopies = deleteStaleLocalCopies();
            final int approvals = expireApprovals(now);
            LOGGER.info(() -> JOB_NAME + ": removed " + files + " expired workspace file(s), deleted " + chats
                              + " old chat(s), removed the data of " + removedUsers + " deleted user(s) and "
                              + localCopies + " stale local file(s), and expired " + approvals
                              + " unanswered request(s) for approval");
        });
    }

    int expireFiles(final long now) {
        int count = 0;
        List<Integer> expired = workspaceDao.findExpired(now, BATCH_SIZE);
        while (!expired.isEmpty()) {
            for (final int fileId : expired) {
                workspaceStore.makeUnavailable(fileId, "it reached its retention age");
                count++;
            }
            expired = expired.size() < BATCH_SIZE
                    ? List.of()
                    : workspaceDao.findExpired(now, BATCH_SIZE);
        }
        return count;
    }

    int deleteOldChats(final long now) {
        final StroomDuration retention = NullSafe.get(aiConfigProvider.get(), AiConfig::getChatRetention);
        if (retention == null || retention.toMillis() <= 0) {
            return 0;
        }
        int count = 0;
        final Set<Integer> skipped = new HashSet<>();
        List<Integer> old = aiDao.findChatsNotUpdatedSince(now - retention.toMillis(), BATCH_SIZE);
        while (!old.isEmpty()) {
            boolean deletedAny = false;
            for (final int chatId : old) {
                if (!skipped.contains(chatId)) {
                    if (deleteChat(chatId)) {
                        count++;
                        deletedAny = true;
                    } else {
                        skipped.add(chatId);
                    }
                }
            }
            old = deletedAny && old.size() == BATCH_SIZE
                    ? aiDao.findChatsNotUpdatedSince(now - retention.toMillis(), BATCH_SIZE)
                    : List.of();
        }
        return count;
    }

    int deleteDataOfRemovedUsers() {
        final Set<String> users = new HashSet<>(aiDao.getChatOwners());
        users.addAll(memoryDao.getUserUuids());
        int count = 0;
        for (final String userUuid : users) {
            if (userUuid != null && userService.loadByUuid(userUuid).isEmpty()) {
                LOGGER.info(() -> "Deleting the 'Ask Stroom AI' chats and memory of deleted user " + userUuid);
                aiDao.getChatIds(userUuid).forEach(this::deleteChat);
                memoryDao.deleteAllForUser(userUuid);
                count++;
            }
        }
        return count;
    }

    /// Expires requests for the user's approval that have not been answered in time. Tool calls waiting for
    /// approval are not made and their run stops; memory changes are not made.
    int expireApprovals(final long now) {
        int count = 0;
        for (final AiChatMessage message : aiDao.getMessagesOfTypeBefore(AiMessageType.APPROVAL_REQUEST,
                now - ToolApprovalService.EXPIRY.toMillis())) {
            final ToolApprovalRequest toolRequest = ToolApprovalService.read(message).orElse(null);
            if (toolRequest != null) {
                if (toolRequest.status() == ToolApprovalRequest.Status.PENDING) {
                    toolApprovalService.expire(message, toolRequest);
                    count++;
                }
            } else {
                final MemoryChange change = AiMemoryService.readChange(message.getMessage()).orElse(null);
                if (change != null && change.status() == ApprovalStatus.PENDING
                    && aiDao.compareAndSetMessageText(message.getId(), message.getMessage(),
                        JsonUtil.writeValueAsString(change.withStatus(ApprovalStatus.EXPIRED), false))) {
                    count++;
                }
            }
        }
        return count;
    }

    /// Deletes this node's local copies of workspace files whose content is no longer held, e.g. because the file
    /// expired or its chat was deleted on another node.
    int deleteStaleLocalCopies() {
        final Path dir = fileStore.getAttachmentDir();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        final List<Integer> localIds = new ArrayList<>();
        try (final Stream<Path> files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d{1,9}\\.md"))
                    .map(name -> Integer.parseInt(name.substring(0, name.length() - 3)))
                    .forEach(localIds::add);
        } catch (final IOException e) {
            LOGGER.warn(() -> "Unable to list " + dir + ": " + e.getMessage(), e);
            return 0;
        }
        int count = 0;
        for (int from = 0; from < localIds.size(); from += BATCH_SIZE) {
            final List<Integer> batch = localIds.subList(from, Math.min(localIds.size(), from + BATCH_SIZE));
            final Set<Integer> available = new HashSet<>(workspaceDao.findAvailable(batch));
            for (final int id : batch) {
                if (!available.contains(id)) {
                    fileStore.deleteAttachmentFile(id);
                    count++;
                }
            }
        }
        return count;
    }

    /// @return True if the chat was deleted, false if a run is working on it.
    private boolean deleteChat(final int chatId) {
        if (runDao.getActive(chatId).isPresent()) {
            LOGGER.debug(() -> "Not deleting chatId=" + chatId + " as a run is working on it");
            return false;
        }
        final List<Integer> fileIds = aiDao.getAttachmentsByChatId(chatId).stream()
                .map(AiChatAttachment::getId)
                .toList();
        aiDao.deleteChat(chatId);
        mcpClientCache.evictChat(chatId);
        fileStore.deleteAttachmentFiles(fileIds);
        LOGGER.debug(() -> "Deleted chatId=" + chatId);
        return true;
    }
}
