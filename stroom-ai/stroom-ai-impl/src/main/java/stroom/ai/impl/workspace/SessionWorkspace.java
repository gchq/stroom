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

import stroom.ai.impl.workspace.ChatWorkspace.Entry;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// The workspace of one session, as a tool call sees it: the files of a [ChatWorkspace] for one chat, so a tool
/// doesn't need to know which chat it is called in, and can only reach that chat's files. The workspace is still
/// held by chat (see [ChatWorkspace]).
///
/// One is made for each tool call, and it keeps the files written through it (and not since deleted), so the
/// call's result can say which files it wrote (see [#written()]).
public final class SessionWorkspace {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_NAME_TRIES = 1000;
    // A hold not released by a write (the call failed first) is dropped after this.
    private static final Duration STALE_HOLD = Duration.ofHours(1);
    // The paths held by calls for files they are about to write, by chat, for every session on this node: each call
    // has its own SessionWorkspace, so the holds are shared between them. The value is when the path was held.
    private static final ConcurrentHashMap<Hold, Instant> HELD = new ConcurrentHashMap<>();

    private final ChatWorkspace workspace;
    private final int chatId;
    // The files written through this, in the order first written, less any since deleted.
    private final Set<WorkspacePath> written = Collections.synchronizedSet(new LinkedHashSet<>());

    /// @param workspace The workspaces of all chats.
    /// @param chatId    The chat whose workspace this is.
    public SessionWorkspace(final ChatWorkspace workspace, final int chatId) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.chatId = chatId;
    }

    /// A new file's path for a tool's output, named by the time: `<prefix>-<yyyyMMdd-HHmmss><extension>`, or with
    /// `-2`, `-3` and so on before the extension when a file is already at that path in this chat's workspace, or
    /// another call has the path for a file it is about to write. The path is held for this call until it writes a
    /// file there, so calls made at once, in the same second, each get their own path.
    ///
    /// @param prefix    The path up to the time, e.g. `/outputs/queries/query`.
    /// @param extension The extension, with its dot, e.g. `.md`.
    /// @return The path, held for this call.
    public WorkspacePath newFile(final String prefix, final String extension) {
        return newFile(prefix, extension, Instant.now());
    }

    // As newFile(prefix, extension), at a given time.
    WorkspacePath newFile(final String prefix, final String extension, final Instant now) {
        final String base = prefix + "-" + FILE_TIME.format(now.atZone(ZoneOffset.UTC));
        releaseStaleHolds(now);
        for (int n = 1; n <= MAX_NAME_TRIES; n++) {
            final WorkspacePath path = WorkspacePath.parse(n == 1
                    ? base + extension
                    : base + "-" + n + extension);
            if (workspace.find(chatId, path).isEmpty() && hold(path, now)) {
                if (workspace.find(chatId, path).isEmpty()) {
                    return path;
                }
                // Written by another call between the check and the hold.
                release(path);
            }
        }
        throw new WorkspaceException("There is no free name for a new file at " + base + extension + ".");
    }

    /// @param dir The directory (or area, or the root) to list.
    /// @return The files at or below it, in path order.
    public List<Entry> list(final WorkspacePath dir) {
        return workspace.list(chatId, dir);
    }

    /// @param path A file's path.
    /// @return The file's id.
    /// @throws WorkspaceException if there is no such file.
    public int fileId(final WorkspacePath path) {
        return workspace.fileId(chatId, path);
    }

    /// @param path A file's path.
    /// @return The file's id, if there is a file at the path.
    public Optional<Integer> find(final WorkspacePath path) {
        return workspace.find(chatId, path);
    }

    /// @param path     A file's path.
    /// @param fromLine The first line to read, from 0.
    /// @param max      The most lines to read.
    /// @return Lines of the file.
    public List<String> readLines(final WorkspacePath path, final long fromLine, final int max) {
        return workspace.readLines(chatId, path, fromLine, max);
    }

    /// @param path A file's path.
    /// @return How many lines the file has.
    public long lineCount(final WorkspacePath path) {
        return workspace.lineCount(chatId, path);
    }

    /// @param path A file's path.
    /// @return The file's size in bytes.
    public long size(final WorkspacePath path) {
        return workspace.size(chatId, path);
    }

    /// @param path A file's path.
    /// @return The file's source document (for files made from it to record), or null.
    public String sourceDocUuid(final WorkspacePath path) {
        return workspace.sourceDocUuid(chatId, path);
    }

    /// @param path A file's path.
    /// @return This node's local copy of the file, for reading it all (e.g. to query it).
    public Path localFile(final WorkspacePath path) {
        return workspace.localFile(chatId, path);
    }

    /// Writes a file, replacing any already at the path.
    ///
    /// @param path          Where to write it; must be in a writable area.
    /// @param content       The content.
    /// @param sourceDocUuid The UUID of the document the content came from, or null.
    /// @return The file's id.
    public int write(final WorkspacePath path, final String content, final String sourceDocUuid) {
        final int fileId = workspace.write(chatId, path, content, sourceDocUuid);
        written.add(path);
        release(path);
        return fileId;
    }

    /// Writes a file from content already in a (temporary) file, replacing any already at the path.
    ///
    /// @param path          Where to write it; must be in a writable area.
    /// @param content       A file holding the content.
    /// @param sourceDocUuid The UUID of the document the content came from, or null.
    /// @return The file's id.
    public int write(final WorkspacePath path, final Path content, final String sourceDocUuid) {
        final int fileId = workspace.write(chatId, path, content, sourceDocUuid);
        written.add(path);
        release(path);
        return fileId;
    }

    /// Saves the content of a Stroom document a tool has read (see [ChatWorkspace#writeDocument]).
    ///
    /// @param path          Where to save it; must be a file in /documents/.
    /// @param content       The content.
    /// @param sourceDocUuid The UUID of the document the content came from.
    /// @return The file's id.
    public int writeDocument(final WorkspacePath path, final String content, final String sourceDocUuid) {
        final int fileId = workspace.writeDocument(chatId, path, content, sourceDocUuid);
        written.add(path);
        release(path);
        return fileId;
    }

    /// Saves part of what the user sees in a tab (see [ChatWorkspace#writeTabFile]).
    ///
    /// @param path          Where to save it; must be a file in /tabs/.
    /// @param content       A file holding the content, which is copied.
    /// @param sourceDocUuid The UUID of the document the content came from.
    /// @return The file's id.
    public int writeTabFile(final WorkspacePath path, final Path content, final String sourceDocUuid) {
        final int fileId = workspace.writeTabFile(chatId, path, content, sourceDocUuid);
        written.add(path);
        release(path);
        return fileId;
    }

    /// Saves part of what the user sees in a tab, from text (see [ChatWorkspace#writeTabFile]).
    ///
    /// @param path          Where to save it; must be a file in /tabs/.
    /// @param content       The content.
    /// @param sourceDocUuid The UUID of the document the content came from.
    /// @return The file's id.
    public int writeTabFile(final WorkspacePath path, final String content, final String sourceDocUuid) {
        final int fileId = workspace.writeTabFile(chatId, path, content, sourceDocUuid);
        written.add(path);
        release(path);
        return fileId;
    }

    /// Deletes a file.
    ///
    /// @param path The file's path; must be in a writable area.
    public void delete(final WorkspacePath path) {
        workspace.delete(chatId, path);
        written.remove(path);
    }

    /// @return The files written through this (i.e. by the call it was made for) that are still there, in the
    /// order they were first written.
    public List<WorkspacePath> written() {
        synchronized (written) {
            return List.copyOf(written);
        }
    }

    /// @return A new temporary file for a tool to write its output to before it is stored.
    public Path tempFile() {
        return workspace.tempFile();
    }

    /// @param path A path.
    /// @throws WorkspaceException if the path is not a file in a writable area.
    public void checkWritable(final WorkspacePath path) {
        workspace.checkWritable(path);
    }

    // Holds a path for this chat; false if another call holds it.
    private boolean hold(final WorkspacePath path, final Instant now) {
        return HELD.putIfAbsent(new Hold(chatId, path.path()), now) == null;
    }

    // Releases this chat's hold on a path, if there is one.
    private void release(final WorkspacePath path) {
        HELD.remove(new Hold(chatId, path.path()));
    }

    private static void releaseStaleHolds(final Instant now) {
        final Instant stale = now.minus(STALE_HOLD);
        HELD.values().removeIf(heldAt -> heldAt.isBefore(stale));
    }


    // --------------------------------------------------------------------------------


    // A chat's hold on a path.
    private record Hold(int chatId, String path) {

    }
}
