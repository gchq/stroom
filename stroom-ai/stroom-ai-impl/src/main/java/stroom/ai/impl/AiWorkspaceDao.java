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

import stroom.ai.shared.AiAttachmentType;

import java.util.List;
import java.util.Optional;

/// Stores the content of the files in "Ask Stroom AI" chat workspaces. A file's index entry is a row in
/// `ai_chat_attachment` and its content is held in `ai_chat_file_chunk` as compressed chunks of whole lines, so
/// any node can read any chat's files and a range of lines can be read without reading the whole file.
public interface AiWorkspaceDao {

    /// Creates the index entry for a new workspace file. Its content is stored with [#storeContent].
    ///
    /// @param chatId          The chat.
    /// @param type            The type of file.
    /// @param path            The file's path in the workspace, e.g. `/outputs/q1.csv`.
    /// @param sourceMessageId The tool call message that made the file, or null.
    /// @return The new file's id (its attachment id).
    int createFile(int chatId, AiAttachmentType type, String path, Integer sourceMessageId);

    /// @param chatId The chat.
    /// @param path   A path in the chat's workspace.
    /// @return The id of the file with the path, if there is one.
    Optional<Integer> findFile(int chatId, String path);

    /// Stores a file's content, replacing any it had, and records its path and size.
    ///
    /// @param fileId    The file's id (its attachment id).
    /// @param path      The file's path in the workspace.
    /// @param chunks    The content, as chunks in order.
    /// @param sizeBytes The uncompressed size of the whole file.
    void storeContent(int fileId, String path, List<Chunk> chunks, long sizeBytes);

    /// @param fileId The file's id.
    /// @return The file's chunks in order, without their data.
    List<ChunkInfo> getChunkInfo(int fileId);

    /// @param fileId The file's id.
    /// @param seq    The chunk's position in the file, from 0.
    /// @return The chunk's compressed data, if there is such a chunk.
    Optional<byte[]> getChunkData(int fileId, int seq);

    /// @param chatId The chat.
    /// @return The uncompressed size of all the files in the chat's workspace.
    long getTotalSize(int chatId);

    /// A chunk of a file, to store.
    ///
    /// @param firstLine The line number of the chunk's first line in the file, from 0.
    /// @param lineCount How many lines the chunk holds.
    /// @param rawSize   The chunk's uncompressed size in bytes.
    /// @param data      The chunk's compressed data.
    record Chunk(long firstLine, int lineCount, int rawSize, byte[] data) {

    }

    /// A stored chunk, without its data.
    ///
    /// @param seq       The chunk's position in the file, from 0.
    /// @param firstLine The line number of the chunk's first line in the file, from 0.
    /// @param lineCount How many lines the chunk holds.
    /// @param rawSize   The chunk's uncompressed size in bytes.
    record ChunkInfo(int seq, long firstLine, int lineCount, int rawSize) {

    }
}
