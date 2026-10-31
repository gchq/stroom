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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The workspace of one session, as a tool call sees it: the files of a [ChatWorkspace] for one chat, so a tool
/// doesn't need to know which chat it is called in, and can only reach that chat's files. The workspace is still
/// held by chat (see [ChatWorkspace]).
///
/// One is made for each tool call, and it keeps the files written through it (and not since deleted), so the
/// call's result can say which files it wrote (see [#written()]).
public final class SessionWorkspace {

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
}
