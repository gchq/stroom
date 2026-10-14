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
import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.AiWorkspaceDao.Chunk;
import stroom.ai.impl.AiWorkspaceDao.ChunkInfo;
import stroom.ai.shared.AiAttachmentType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.luben.zstd.Zstd;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/// The files in "Ask Stroom AI" chat workspaces. File content is stored in the database as zstd-compressed chunks
/// of whole lines (so any node can read any chat's files), and a node keeps a local copy of the files it uses in
/// [AiAttachmentFileStore], which is rebuilt from the database when it is missing, e.g. on another node or after a
/// restart.
@Singleton
public class WorkspaceStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WorkspaceStore.class);

    /// Chunks are cut at the first line end after this many bytes.
    static final int CHUNK_SIZE_BYTES = 1_048_576;
    private static final int COMPRESSION_LEVEL = 3;

    private final AiWorkspaceDao workspaceDao;
    private final AiAttachmentFileStore fileStore;

    @Inject
    public WorkspaceStore(final AiWorkspaceDao workspaceDao,
                          final AiAttachmentFileStore fileStore) {
        this.workspaceDao = workspaceDao;
        this.fileStore = fileStore;
    }

    /// Creates a workspace file with the given content.
    ///
    /// @param chatId          The chat.
    /// @param type            The type of file.
    /// @param path            The file's path in the workspace.
    /// @param sourceMessageId The tool call message that made the file, or null.
    /// @param content         The file's content.
    /// @return The new file's id.
    public int createFile(final int chatId,
                          final AiAttachmentType type,
                          final String path,
                          final Integer sourceMessageId,
                          final String content) {
        final int fileId = workspaceDao.createFile(chatId, type, path, sourceMessageId);
        final Path localFile = fileStore.createAttachmentFile(fileId);
        try {
            Files.writeString(localFile, content, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to write workspace file " + path, e);
        }
        persist(fileId, path, localFile);
        return fileId;
    }

    /// Stores the content of a local file in the database, so that the file can be read on any node.
    ///
    /// @param fileId    The file's id (its attachment id).
    /// @param path      The file's path in the workspace.
    /// @param localFile The file's content.
    public void persist(final int fileId, final String path, final Path localFile) {
        try (final BufferedReader reader = Files.newBufferedReader(localFile, StandardCharsets.UTF_8)) {
            final List<Chunk> chunks = new ArrayList<>();
            final StringBuilder chunk = new StringBuilder();
            long firstLine = 0;
            int lineCount = 0;
            int chunkBytes = 0;
            long totalBytes = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                chunk.append(line).append('\n');
                lineCount++;
                chunkBytes += utf8Length(line) + 1;
                if (chunkBytes >= CHUNK_SIZE_BYTES) {
                    chunks.add(toChunk(chunk, firstLine, lineCount));
                    totalBytes += chunkBytes;
                    firstLine += lineCount;
                    lineCount = 0;
                    chunkBytes = 0;
                    chunk.setLength(0);
                }
            }
            if (lineCount > 0) {
                chunks.add(toChunk(chunk, firstLine, lineCount));
                totalBytes += chunkBytes;
            }
            workspaceDao.storeContent(fileId, path, chunks, totalBytes);
            final long size = totalBytes;
            LOGGER.debug(() -> "Stored workspace file " + path + " (" + size + " bytes) in " + chunks.size()
                               + " chunk(s)");
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to store workspace file " + path, e);
        }
    }

    /// @param fileId The file's id.
    /// @return The node's local copy of the file, rebuilt from the database if it is missing. The path does not
    /// exist if the file has no stored content either.
    public Path localFile(final int fileId) {
        final Path localFile = fileStore.getAttachmentFile(fileId);
        if (Files.exists(localFile)) {
            return localFile;
        }
        final List<ChunkInfo> chunks = workspaceDao.getChunkInfo(fileId);
        if (chunks.isEmpty()) {
            return localFile;
        }
        LOGGER.debug(() -> "Rebuilding local copy of workspace file " + fileId + " from " + chunks.size()
                           + " chunk(s)");
        final Path tempFile = fileStore.createAttachmentFile(fileId).resolveSibling(fileId + ".rebuilding");
        try (final BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            for (final ChunkInfo chunk : chunks) {
                writer.write(readChunk(fileId, chunk));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to rebuild workspace file " + fileId, e);
        }
        try {
            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to rebuild workspace file " + fileId, e);
        }
        return localFile;
    }

    /// Reads a range of lines of a file, reading only the chunks that hold them.
    ///
    /// @param fileId    The file's id.
    /// @param fromLine  The first line to read, from 0.
    /// @param maxLines  The most lines to read.
    /// @return The lines, without line ends. Fewer than asked for if the file ends first.
    public List<String> readLines(final int fileId, final long fromLine, final int maxLines) {
        final List<String> lines = new ArrayList<>();
        final long toLine = fromLine + maxLines;
        for (final ChunkInfo chunk : workspaceDao.getChunkInfo(fileId)) {
            final long chunkEnd = chunk.firstLine() + chunk.lineCount();
            if (chunkEnd <= fromLine) {
                continue;
            }
            if (chunk.firstLine() >= toLine) {
                break;
            }
            try (final BufferedReader reader = new BufferedReader(new StringReader(readChunk(fileId, chunk)))) {
                long lineNo = chunk.firstLine();
                String line;
                while ((line = reader.readLine()) != null && lineNo < toLine) {
                    if (lineNo >= fromLine) {
                        lines.add(line);
                    }
                    lineNo++;
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return lines;
    }

    /// @param fileId The file's id.
    /// @return How many lines the file has.
    public long lineCount(final int fileId) {
        return workspaceDao.getChunkInfo(fileId).stream()
                .mapToLong(ChunkInfo::lineCount)
                .sum();
    }

    private String readChunk(final int fileId, final ChunkInfo chunk) {
        final byte[] compressed = workspaceDao.getChunkData(fileId, chunk.seq())
                .orElseThrow(() -> new IllegalStateException(
                        "Chunk " + chunk.seq() + " of workspace file " + fileId + " is missing"));
        return new String(Zstd.decompress(compressed, chunk.rawSize()), StandardCharsets.UTF_8);
    }

    private static Chunk toChunk(final CharSequence text, final long firstLine, final int lineCount) {
        final byte[] raw = text.toString().getBytes(StandardCharsets.UTF_8);
        return new Chunk(firstLine, lineCount, raw.length, Zstd.compress(raw, COMPRESSION_LEVEL));
    }

    private static int utf8Length(final String line) {
        int bytes = 0;
        for (int i = 0; i < line.length(); i++) {
            final char chr = line.charAt(i);
            if (chr < 0x80) {
                bytes++;
            } else if (chr < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(chr)) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }
}
