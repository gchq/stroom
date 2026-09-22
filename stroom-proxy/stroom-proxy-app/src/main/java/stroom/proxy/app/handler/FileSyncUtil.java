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

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.stream.Stream;

/// Helpers for forcing file data and directory entries to durable storage (`fsync`).
///
/// Writing a file and closing it only guarantees that the data has reached the operating
/// system's page cache, not the physical device. Two separate things must be forced to get
/// a durability guarantee:
///
/// * The **contents** of the file itself, see [#syncFile(Path)].
/// * The **directory entry** that names the file, see [#syncDir(Path)]. This is easily
///   overlooked. Creating or renaming a file is a modification of its parent directory, so
///   until that directory is synced the rename can still be lost even though the file
///   contents are safely on disk. As the proxy uses a write-then-atomic-move idiom to commit
///   data between pipeline phases, syncing the directory is what makes the move durable.
public final class FileSyncUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileSyncUtil.class);

    private FileSyncUtil() {
        // Utility class.
    }

    /// Forces the contents of `file` to durable storage.
    ///
    /// It is safe to call this after the stream that wrote the file has been closed. `fsync`
    /// operates on the underlying file rather than on a particular handle, so re-opening the
    /// file purely to force it will still flush any pages dirtied by the original writer.
    ///
    /// @param file The file to force to disk. Must not be null and must exist.
    /// @throws IOException If the file cannot be opened or forced.
    public static void syncFile(final Path file) throws IOException {
        Objects.requireNonNull(file);
        try (final FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        LOGGER.trace(() -> LogUtil.message("syncFile() - Synced {}", LogUtil.path(file)));
    }

    /// Forces the contents of `file` to durable storage, doing nothing if it does not exist.
    ///
    /// @param file The file to force to disk. Must not be null.
    /// @throws IOException If the file exists but cannot be opened or forced.
    public static void syncFileIfExists(final Path file) throws IOException {
        Objects.requireNonNull(file);
        if (Files.isRegularFile(file)) {
            syncFile(file);
        } else {
            LOGGER.trace(() -> LogUtil.message("syncFileIfExists() - Skipping {}, it does not exist",
                    LogUtil.path(file)));
        }
    }

    /// Forces every regular file directly inside `dir` to durable storage.
    ///
    /// Sub-directories are not descended into.
    ///
    /// @param dir The directory whose files should be forced to disk. Must not be null.
    /// @throws IOException If the directory cannot be listed or a file cannot be forced.
    public static void syncDirContents(final Path dir) throws IOException {
        Objects.requireNonNull(dir);
        try (final Stream<Path> children = Files.list(dir)) {
            for (final Path child : children.toList()) {
                syncFileIfExists(child);
            }
        }
    }

    /// Forces `dir` and each of its ancestors up to, and including, `stopAt` to durable storage.
    ///
    /// Creating a directory is a change to *its* parent, so forcing only the directory an entry was
    /// created in is not enough when the intervening directories are themselves new. `stopAt` is
    /// forced as well, and walking stops there or at the filesystem root, whichever comes first.
    ///
    /// @param dir    The deepest directory to force. Must not be null.
    /// @param stopAt The highest directory to force, typically a long lived root. Must not be null.
    public static void syncDirTree(final Path dir, final Path stopAt) {
        Objects.requireNonNull(dir);
        Objects.requireNonNull(stopAt);
        final Path normalisedStopAt = stopAt.toAbsolutePath().normalize();
        Path current = dir.toAbsolutePath().normalize();
        while (current != null) {
            syncDir(current);
            if (current.equals(normalisedStopAt)) {
                break;
            }
            current = current.getParent();
        }
    }

    /// Forces the directory entries of `dir` to durable storage.
    ///
    /// Call this after creating, renaming or deleting an entry inside `dir`, otherwise that
    /// change can still be lost on power failure even if the file contents were synced.
    ///
    /// Not all platforms allow a directory to be opened as a channel; Windows in particular
    /// does not. There is no way to force a directory there, so rather than fail the receipt
    /// we log the fact and carry on. The proxy is deployed on Linux, where this is supported.
    ///
    /// @param dir The directory to force to disk. Must not be null.
    public static void syncDir(final Path dir) {
        Objects.requireNonNull(dir);
        try (final FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
            LOGGER.trace(() -> LogUtil.message("syncDir() - Synced {}", LogUtil.path(dir)));
        } catch (final IOException e) {
            // Don't propagate, as not all platforms allow a dir to be opened as a channel and we
            // would rather carry on than fail a receipt. Log at WARN though, otherwise a genuine
            // and persistent failure would silently turn off the durability that was asked for.
            LOGGER.warn(() -> LogUtil.message(
                    "syncDir() - Unable to sync directory {}, so changes to it may not be durable. " +
                    "Either this platform does not support forcing a directory or the sync failed: {}",
                    LogUtil.path(dir), LogUtil.exceptionMessage(e)));
            LOGGER.debug(e::getMessage, e);
        }
    }
}
