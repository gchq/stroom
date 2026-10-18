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
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.AiWorkspaceDao.FileInfo;
import stroom.ai.impl.workspace.WorkspacePath.Area;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/// A chat's workspace, as the agent's tools see it: files under virtual paths ([WorkspacePath]) in areas with
/// their own access ({@code /attachments} is read-only, {@code /outputs} can be written), within size limits.
///
/// Everything goes through here and [WorkspaceStore], so every file is checked before it is used (S6), every
/// path is checked strictly, and a tool can only ever reach the workspace of the chat it is called in. Memory
/// ({@code /memories}) is held separately, see AiMemoryService.
public class ChatWorkspace {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ChatWorkspace.class);

    private final AiWorkspaceDao workspaceDao;
    private final WorkspaceStore workspaceStore;
    private final AiService aiService;
    private final Provider<AiConfig> aiConfigProvider;
    private final TempDirProvider tempDirProvider;

    @Inject
    ChatWorkspace(final AiWorkspaceDao workspaceDao,
                  final WorkspaceStore workspaceStore,
                  final AiService aiService,
                  final Provider<AiConfig> aiConfigProvider,
                  final TempDirProvider tempDirProvider) {
        this.workspaceDao = workspaceDao;
        this.workspaceStore = workspaceStore;
        this.aiService = aiService;
        this.aiConfigProvider = aiConfigProvider;
        this.tempDirProvider = tempDirProvider;
    }

    /// @return The workspace limits.
    public AiWorkspaceConfig limits() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getWorkspace, new AiWorkspaceConfig());
    }

    /// Does some work within the tool time limit.
    ///
    /// @throws ToolTimeoutException if it runs past the limit.
    public <T> T withTimeLimit(final Supplier<T> work) {
        return withTimeLimit(limits().getToolTimeLimit().getDuration(), work);
    }

    /// Does some work within a time limit.
    ///
    /// @throws ToolTimeoutException if it runs past the limit.
    public <T> T withTimeLimit(final Duration limit, final Supplier<T> work) {
        return ToolDeadline.run(limit, work);
    }

    /// @param chatId The chat.
    /// @param dir    The directory (or area, or the root) to list.
    /// @return The files at or below it, in path order.
    public List<Entry> list(final int chatId, final WorkspacePath dir) {
        rejectMemories(dir);
        final List<Entry> entries = new ArrayList<>();
        for (final FileInfo info : workspaceDao.listFiles(chatId)) {
            final WorkspacePath path = tryParse(info.path());
            if (path != null && dir.contains(path)) {
                entries.add(new Entry(path, info.sizeBytes(), info.status() == AiAttachmentStatus.UNAVAILABLE
                        ? NullSafe.nonBlankStringElse(info.errorMessage(), "removed")
                        : null));
            }
        }
        return entries;
    }

    /// @param chatId The chat.
    /// @param path   A file's path.
    /// @return The file's id.
    /// @throws WorkspaceException if there is no such file.
    /// @throws WorkspaceFileUnavailableException if its content is no longer held or may no longer be used.
    public int fileId(final int chatId, final WorkspacePath path) {
        rejectMemories(path);
        if (!path.isFile()) {
            throw new WorkspaceException(path + " is a directory. Give the path of a file.");
        }
        final int fileId = find(chatId, path)
                .orElseThrow(() -> new WorkspaceException("There is no file " + path + ". List the workspace "
                                                          + "to see what there is."));
        workspaceStore.checkAvailable(fileId);
        return fileId;
    }

    /// @return The file's id, if the chat has a file at the path.
    public Optional<Integer> find(final int chatId, final WorkspacePath path) {
        return workspaceDao.findFile(chatId, path.path());
    }

    /// @return Lines of a file, from 0.
    public List<String> readLines(final int chatId, final WorkspacePath path, final long fromLine, final int max) {
        return workspaceStore.readLines(fileId(chatId, path), fromLine, max);
    }

    /// @return How many lines a file has.
    public long lineCount(final int chatId, final WorkspacePath path) {
        return workspaceStore.lineCount(fileId(chatId, path));
    }

    /// @return The file's size in bytes.
    public long size(final int chatId, final WorkspacePath path) {
        return workspaceDao.getSize(fileId(chatId, path));
    }

    /// @return The file's source document (for files made from it to record), or null.
    public String sourceDocUuid(final int chatId, final WorkspacePath path) {
        return workspaceDao.getFileInfo(fileId(chatId, path))
                .map(FileInfo::sourceDocUuid)
                .orElse(null);
    }

    /// @return This node's local copy of a file, for reading it all (e.g. to query it).
    public Path localFile(final int chatId, final WorkspacePath path) {
        return workspaceStore.localFile(fileId(chatId, path));
    }

    /// Writes a file, replacing any already at the path.
    ///
    /// @param chatId        The chat.
    /// @param path          Where to write it; must be in a writable area.
    /// @param content       The content.
    /// @param sourceDocUuid The UUID of the document the content came from, or null.
    /// @return The file's id.
    public int write(final int chatId, final WorkspacePath path, final String content, final String sourceDocUuid) {
        final Path temp = tempFile();
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            return write(chatId, path, temp, sourceDocUuid);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /// Writes a file from content already in a (temporary) file, replacing any already at the path.
    ///
    /// @return The file's id.
    public int write(final int chatId, final WorkspacePath path, final Path content, final String sourceDocUuid) {
        checkWritable(path);
        return store(chatId, path, content, sourceDocUuid);
    }

    /// Saves the content of a Stroom document a tool has read, in the read-only [Area#DOCUMENTS] area, replacing
    /// any file already at the path. The file records the document as its source, so the user's access to the
    /// document is checked again whenever the file is used.
    ///
    /// @param chatId        The chat.
    /// @param path          Where to save it; must be a file in [Area#DOCUMENTS].
    /// @param content       The content.
    /// @param sourceDocUuid The UUID of the document the content came from.
    /// @return The file's id.
    public int writeDocument(final int chatId,
                             final WorkspacePath path,
                             final String content,
                             final String sourceDocUuid) {
        if (!path.isFile() || path.area() != Area.DOCUMENTS) {
            throw new WorkspaceException(path + " is not a file in /documents/.");
        }
        final Path temp = tempFile();
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            return store(chatId, path, temp, sourceDocUuid);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /// Saves part of what the user sees in a tab (e.g. a snapshot of its results), in the read-only [Area#TABS]
    /// area, replacing any file already at the path. The file records the document its content came from, so the
    /// user's access to it is checked again whenever the file is used.
    ///
    /// @param chatId        The chat.
    /// @param path          Where to save it; must be a file in [Area#TABS].
    /// @param content       A file holding the content, which is copied.
    /// @param sourceDocUuid The UUID of the document the content came from.
    /// @return The file's id.
    public int writeTabFile(final int chatId,
                            final WorkspacePath path,
                            final Path content,
                            final String sourceDocUuid) {
        if (!path.isFile() || path.area() != Area.TABS) {
            throw new WorkspaceException(path + " is not a file in /tabs/.");
        }
        return store(chatId, path, content, sourceDocUuid, AiAttachmentType.TAB_SNAPSHOT);
    }

    /// As [#writeTabFile(int, WorkspacePath, Path, String)], from text.
    ///
    /// @return The file's id.
    public int writeTabFile(final int chatId,
                            final WorkspacePath path,
                            final String content,
                            final String sourceDocUuid) {
        final Path temp = tempFile();
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            return writeTabFile(chatId, path, temp, sourceDocUuid);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(temp);
        }
    }

    // Stores a file at a path already checked, within the limits.
    private int store(final int chatId, final WorkspacePath path, final Path content, final String sourceDocUuid) {
        return store(chatId, path, content, sourceDocUuid, AiAttachmentType.WORKSPACE_FILE);
    }

    // Stores a file of a type at a path already checked, within the limits.
    private int store(final int chatId,
                      final WorkspacePath path,
                      final Path content,
                      final String sourceDocUuid,
                      final AiAttachmentType type) {
        final long size;
        try {
            size = Files.size(content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final Optional<Integer> existing = find(chatId, path);
        checkLimits(chatId, existing.orElse(null), size);
        if (existing.isPresent()) {
            workspaceStore.replaceContent(existing.get(), path.path(), sourceDocUuid, content);
            LOGGER.debug(() -> "Replaced " + path + " in chatId=" + chatId);
            return existing.get();
        }
        final int fileId = workspaceStore.createFile(chatId, type, path.path(), null, sourceDocUuid, content);
        LOGGER.debug(() -> "Wrote " + path + " in chatId=" + chatId);
        return fileId;
    }

    /// Deletes a file.
    public void delete(final int chatId, final WorkspacePath path) {
        checkWritable(path);
        final int fileId = find(chatId, path)
                .orElseThrow(() -> new WorkspaceException("There is no file " + path + "."));
        workspaceDao.deleteFile(fileId);
        workspaceStore.deleteLocalCopy(fileId);
    }

    /// @return A new temporary file for a tool to write its output to before it is stored.
    public Path tempFile() {
        try {
            final Path dir = tempDirProvider.get().resolve("ai-workspace-tmp");
            Files.createDirectories(dir);
            return Files.createTempFile(dir, "tool", ".tmp");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Deletes a temporary file, ignoring any problem.
    public static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException e) {
            LOGGER.debug(() -> "Unable to delete " + path, e);
        }
    }

    /// @throws WorkspaceException if the path is not a file in a writable area.
    public void checkWritable(final WorkspacePath path) {
        rejectMemories(path);
        if (!path.isFile()) {
            throw new WorkspaceException(path + " is not a file path. Write to a file, e.g. /outputs/result.csv.");
        }
        if (!path.area().isWritable()) {
            throw new WorkspaceException(path + " is read-only. Write to /outputs/ instead.");
        }
    }

    /// @throws WorkspaceException if writing a file of the size would go over a limit.
    void checkLimits(final int chatId, final Integer replacingFileId, final long newSize) {
        final AiWorkspaceConfig limits = limits();
        if (newSize > limits.getMaxFileBytes()) {
            throw new WorkspaceException("The file would be " + newSize + " bytes, but files may be at most "
                                         + limits.getMaxFileBytes() + " bytes. Write less, e.g. filter or "
                                         + "summarise the data first.");
        }
        final long replacing = replacingFileId != null
                ? workspaceDao.getSize(replacingFileId)
                : 0;
        final long chatTotal = workspaceDao.getTotalSize(chatId) - replacing + newSize;
        if (chatTotal > limits.getMaxChatBytes()) {
            throw new WorkspaceException("This chat's workspace would hold " + chatTotal + " bytes, but may hold at "
                                         + "most " + limits.getMaxChatBytes() + ". Delete files in /outputs/ "
                                         + "that are no longer needed.");
        }
        final String owner = NullSafe.get(aiService.getChat(chatId), AiChat::getUserUuid);
        if (owner != null) {
            final long userTotal = workspaceDao.getTotalSizeForUser(owner) - replacing + newSize;
            if (userTotal > limits.getMaxUserBytes()) {
                throw new WorkspaceException("Your chat workspaces would hold " + userTotal + " bytes, but may "
                                             + "hold at most " + limits.getMaxUserBytes() + " in all. Delete "
                                             + "files, or chats, that are no longer needed.");
            }
        }
    }

    private static void rejectMemories(final WorkspacePath path) {
        if (path.area() == Area.MEMORIES) {
            throw new WorkspaceException("Memory is not a workspace file. Use the memory tool for "
                                         + path + ".");
        }
    }

    private static WorkspacePath tryParse(final String path) {
        try {
            return WorkspacePath.parse(path);
        } catch (final WorkspaceException e) {
            return null;
        }
    }

    /// A file in the workspace.
    ///
    /// @param path        Its path.
    /// @param sizeBytes   Its size.
    /// @param unavailable Why its content has gone, or null if it is available.
    public record Entry(WorkspacePath path, long sizeBytes, String unavailable) {

    }
}
