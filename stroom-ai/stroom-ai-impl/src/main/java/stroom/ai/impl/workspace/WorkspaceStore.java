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
import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.AiWorkspaceDao.Chunk;
import stroom.ai.impl.AiWorkspaceDao.ChunkInfo;
import stroom.ai.impl.AiWorkspaceDao.FileInfo;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.github.luben.zstd.Zstd;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

    static final String REASON_EXPIRED = "it reached its retention age";
    static final String REASON_NO_ACCESS = "you no longer have access to the data it came from";
    static final String REASON_NOT_CHECKED = "your access to the data it came from couldn't be checked just now; "
                                             + "try again";

    private final AiWorkspaceDao workspaceDao;
    private final AiAttachmentFileStore fileStore;
    private final Provider<AiConfig> aiConfigProvider;
    private final SourceDocAccess sourceDocAccess;

    @Inject
    public WorkspaceStore(final AiWorkspaceDao workspaceDao,
                          final AiAttachmentFileStore fileStore,
                          final Provider<AiConfig> aiConfigProvider,
                          final SourceDocAccess sourceDocAccess) {
        this.workspaceDao = workspaceDao;
        this.fileStore = fileStore;
        this.aiConfigProvider = aiConfigProvider;
        this.sourceDocAccess = sourceDocAccess;
    }

    /// Sets where a file's content came from, and when it expires (the configured retention from now). Files hold
    /// copies of Stroom data, so they expire sooner than their chat, and the user's access to the data they came
    /// from is checked again whenever they are used (S6).
    ///
    /// @param fileId        The file.
    /// @param sourceDocUuid The UUID of the document the content came from (e.g. a data source), or null.
    public void setRetention(final int fileId, final String sourceDocUuid) {
        final StroomDuration retention = NullSafe.get(aiConfigProvider.get(), AiConfig::getWorkspaceFileRetention);
        final Long expireTimeMs = retention != null && retention.toMillis() > 0
                ? System.currentTimeMillis() + retention.toMillis()
                : null;
        workspaceDao.setRetention(fileId, sourceDocUuid, expireTimeMs);
    }

    /// Checks a file can still be used: it has not expired, and the user still has access to the data it came
    /// from (Use on a data source, View on any other document, see [SourceDocAccess]). If not, its content is
    /// removed.
    ///
    /// @param fileId The file.
    /// @throws WorkspaceFileUnavailableException if it cannot be used.
    public void checkAvailable(final int fileId) {
        final FileInfo info = workspaceDao.getFileInfo(fileId).orElse(null);
        if (info == null) {
            return;
        }
        if (info.status() == AiAttachmentStatus.UNAVAILABLE) {
            throw new WorkspaceFileUnavailableException(fileId, info.path(), info.errorMessage());
        }
        if (info.expireTimeMs() != null && info.expireTimeMs() <= System.currentTimeMillis()) {
            makeUnavailable(fileId, REASON_EXPIRED);
            throw new WorkspaceFileUnavailableException(fileId, info.path(), REASON_EXPIRED);
        }
        if (info.sourceDocUuid() != null) {
            final SourceDocAccess.Access access = sourceDocAccess.access(info.sourceDocUuid());
            if (access == SourceDocAccess.Access.DENIED) {
                makeUnavailable(fileId, REASON_NO_ACCESS);
                throw new WorkspaceFileUnavailableException(fileId, info.path(), REASON_NO_ACCESS);
            } else if (access == SourceDocAccess.Access.UNKNOWN) {
                // Not used this time, but kept, as access may well be confirmed next time.
                throw new WorkspaceFileUnavailableException(fileId, info.path(), REASON_NOT_CHECKED);
            }
        }
    }

    /// Deletes this node's local copy of a file, e.g. because the file has been deleted.
    ///
    /// @param fileId The file.
    public void deleteLocalCopy(final int fileId) {
        fileStore.deleteAttachmentFile(fileId);
    }

    /// Removes a file's content, here and on this node's disk, keeping its record and the reason.
    ///
    /// @param fileId The file.
    /// @param reason Why.
    public void makeUnavailable(final int fileId, final String reason) {
        LOGGER.info(() -> "Removing the content of workspace file " + fileId + " as " + reason);
        workspaceDao.makeUnavailable(fileId, reason);
        fileStore.deleteAttachmentFile(fileId);
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
        setRetention(fileId, null);
        final Path localFile = fileStore.createAttachmentFile(fileId);
        try {
            Files.writeString(localFile, content, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to write workspace file " + path, e);
        }
        persist(fileId, path, localFile);
        return fileId;
    }

    /// Creates a file in a chat's workspace from content already in a file.
    ///
    /// @param chatId          The chat.
    /// @param type            The type of file.
    /// @param path            Its path in the workspace.
    /// @param sourceMessageId The tool call message that made it, or null.
    /// @param sourceDocUuid   The UUID of the document its content came from, or null.
    /// @param content         A file holding the content, which is copied.
    /// @return The new file's id.
    public int createFile(final int chatId,
                          final AiAttachmentType type,
                          final String path,
                          final Integer sourceMessageId,
                          final String sourceDocUuid,
                          final Path content) {
        final int fileId = workspaceDao.createFile(chatId, type, path, sourceMessageId);
        replaceContent(fileId, path, sourceDocUuid, content);
        return fileId;
    }

    /// Replaces a file's content, and starts its retention again.
    ///
    /// @param fileId        The file.
    /// @param path          Its path in the workspace.
    /// @param sourceDocUuid The UUID of the document its content came from, or null.
    /// @param content       A file holding the new content, which is copied.
    public void replaceContent(final int fileId, final String path, final String sourceDocUuid, final Path content) {
        final Path localFile = fileStore.createAttachmentFile(fileId);
        try {
            Files.copy(content, localFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to write workspace file " + path, e);
        }
        persist(fileId, path, localFile);
        setRetention(fileId, sourceDocUuid);
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
        checkAvailable(fileId);
        final Path localFile = fileStore.getAttachmentFile(fileId);
        // Never follow a link someone has put in place of a local copy; rebuild it instead.
        if (Files.isSymbolicLink(localFile)) {
            LOGGER.warn(() -> "Replacing symbolic link " + localFile + " with the stored content");
            try {
                Files.delete(localFile);
            } catch (final IOException e) {
                throw new UncheckedIOException("Unable to remove " + localFile, e);
            }
        } else if (Files.exists(localFile, LinkOption.NOFOLLOW_LINKS)) {
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
        checkAvailable(fileId);
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
        checkAvailable(fileId);
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
